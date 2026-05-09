package com.jianbo.localaiknowledge.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

/**
 * Elasticsearch 向量入库服务
 *
 * <p>关键：VectorStore 接口是统一的！ PG 注入的是 PgVectorStore，ES 注入的是 ElasticsearchVectorStore
 * 业务代码一模一样，只是底层存储不同 --> 面向接口编程的威力
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorStoreService {
  /** ElasticsearchVectorStore（由 ES starter 自动配置）；统一向量存储 API。 */
  private final VectorStore vectorStore;

  /** ES 9.x 低级客户端（Apache HttpClient 5）：用于 _delete_by_query 之类 starter 未直接暴露的能力。 */
  private final Rest5Client restClient;

  private static final String INDEX_NAME = "knowledge_vector_store";

  /** 删除文档 */
  public void deleteDocuments(List<String> documentIds) {
    vectorStore.delete(documentIds);
    log.info("ES 已删除 {} 条", documentIds.size());
  }

  /** 按来源名称删除 ES 向量文档（精确按 metadata.source + 可选 user_id 过滤） */
  public void deleteBySource(String source, String userId) throws IOException {
    Request request = new Request("POST", "/" + INDEX_NAME + "/_delete_by_query");

    // ES mapping 中 metadata.source 已是 keyword 类型，term 查询直接匹配
    StringBuilder queryBuilder = new StringBuilder();
    queryBuilder.append("{\"query\":{\"bool\":{\"must\":[");
    queryBuilder
        .append("{\"term\":{\"metadata.source\":\"")
        .append(source.replace("\"", "\\\""))
        .append("\"}}");
    if (userId != null && !userId.isBlank()) {
      queryBuilder
          .append(",{\"term\":{\"metadata.user_id\":\"")
          .append(userId.replace("\"", "\\\""))
          .append("\"}}");
    }
    queryBuilder.append("]}}}");

    request.setJsonEntity(queryBuilder.toString());
    restClient.performRequest(request);
    log.info("ES 已按来源删除: source={}, userId={}", source, userId);
  }

  /**
   * 每批发送给 embedding API 的文档数。
   * 50 chunks × ~800字 × ~1.3 token/字 ≈ 52,000 tokens/batch。
   */
  private static final int EMBED_BATCH_SIZE = 50;

  /** 429 限流时最大重试次数 */
  private static final int MAX_RETRY = 5;

  /** 首次重试等待毫秒数（指数退避基数） */
  private static final long RETRY_BASE_MS = 3000;

  /** 自适应限速：遇到 429 时暂停递增步长（ms） */
  private static final long PAUSE_INCREASE_MS = 2000;
  /** 自适应限速：成功时暂停递减步长（ms） */
  private static final long PAUSE_DECREASE_MS = 500;
  /** 自适应限速：暂停上限（ms） */
  private static final long PAUSE_MAX_MS = 8000;

  /**
   * 以预切片的文本入库（供调用方复用切片结果，避免在 ES / PG 双写场景下重复切片）。
   * 自动分批调用 embedding API，防止大文档一次性打爆 TPM 限制。
   *
   * @return 入库 chunk 数量
   */
  public int importChunks(List<String> chunks, String source, String userId, String docScope) {
    return importChunks(chunks, source, userId, docScope, null);
  }

  /**
   * 分批入库 + 进度回调。
   *
   * <p>自适应限速策略（类似 TCP 拥塞控制）：
   * <ul>
   *   <li>初始暂停 0ms，全速消耗 API 令牌桶的突发容量
   *   <li>遇到 429 → 暂停递增 {@link #PAUSE_INCREASE_MS}ms，并指数退避重试
   *   <li>成功无 429 → 暂停递减 {@link #PAUSE_DECREASE_MS}ms（最低 0）
   *   <li>自动收敛到最优吞吐速率
   * </ul>
   *
   * @param progressCallback 每批完成后回调，参数为已累计入库的 chunk 数；可为 null。
   * @return 入库 chunk 总数
   */
  public int importChunks(List<String> chunks, String source, String userId, String docScope,
                          IntConsumer progressCallback) {
    if (chunks == null || chunks.isEmpty()) return 0;
    List<Document> documents = getDocuments(source, chunks, userId, docScope);

    int total = documents.size();
    int batches = (total + EMBED_BATCH_SIZE - 1) / EMBED_BATCH_SIZE;
    log.info("ES 分批入库开始: 共 {} 段, 分 {} 批（每批 {} 段, 自适应限速）, 来源: {}",
        total, batches, EMBED_BATCH_SIZE, source);

    long pauseMs = 0; // 自适应暂停，初始 0（全速消耗桶容量）

    for (int i = 0; i < total; i += EMBED_BATCH_SIZE) {
      int end = Math.min(i + EMBED_BATCH_SIZE, total);
      List<Document> batch = documents.subList(i, end);
      int batchNo = i / EMBED_BATCH_SIZE + 1;

      // 自适应限速暂停
      if (pauseMs > 0) {
        try { Thread.sleep(pauseMs); } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("ES 入库被中断", ie);
        }
      }

      // 429 兜底：指数退避重试
      boolean hit429 = false;
      for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
        try {
          vectorStore.add(batch);
          break;
        } catch (Exception ex) {
          if (attempt < MAX_RETRY && is429(ex)) {
            hit429 = true;
            long wait = RETRY_BASE_MS * (1L << attempt); // 3s, 6s, 12s, 24s, 48s
            log.warn("ES 批次 {}/{} 触发 429 限流, 第 {} 次重试, 等待 {}ms（当前限速 {}ms）",
                batchNo, batches, attempt + 1, wait, pauseMs);
            try { Thread.sleep(wait); } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw new RuntimeException("ES 入库被中断", ie);
            }
          } else {
            throw ex; // 非 429 或耗尽重试，直接抛出
          }
        }
      }

      // 自适应调整暂停时间
      if (hit429) {
        pauseMs = Math.min(pauseMs + PAUSE_INCREASE_MS, PAUSE_MAX_MS);
      } else {
        pauseMs = Math.max(pauseMs - PAUSE_DECREASE_MS, 0);
      }

      log.info("ES 批次 {}/{} 入库完成（{} 段）, 来源: {}, 限速: {}ms",
          batchNo, batches, batch.size(), source, pauseMs);

      if (progressCallback != null) {
        progressCallback.accept(end);
      }
    }
    log.info("ES 入库完成: {} 段, 来源: {}, userId: {}", total, source, userId);
    return total;
  }

  /** 递归检查异常链是否包含 429 限流 */
  private static boolean is429(Throwable ex) {
    for (Throwable t = ex; t != null; t = t.getCause()) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("429")) return true;
    }
    return false;
  }

  private static @NonNull List<Document> getDocuments(
      String source, List<String> splitText, String userId, String docScope) {
    List<Document> documents = new ArrayList<>();
    for (int i = 0; i < splitText.size(); i++) {
      Map<String, Object> metadata = new HashMap<>();
      metadata.put("source", source);
      metadata.put("chunk_index", String.valueOf(i));
      metadata.put("total_chunks", String.valueOf(splitText.size()));
      metadata.put("doc_scope", docScope != null ? docScope : "PUBLIC");
      if (userId != null) {
        metadata.put("user_id", userId);
      }
      Document document = new Document(splitText.get(i), metadata);
      documents.add(document);
    }
    return documents;
  }
}
