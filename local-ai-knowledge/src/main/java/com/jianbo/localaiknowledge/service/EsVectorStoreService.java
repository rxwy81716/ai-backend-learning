package com.jianbo.localaiknowledge.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

  /** ES 9.x 低级客户端（Apache HttpClient 5）：用于 _delete_by_query 与索引 settings 调整 */
  private final Rest5Client restClient;

  private static final String INDEX_NAME = "knowledge_vector_store";

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

  // ---------- 动态 batch size 与并行度 ----------

  /** 根据 chunk 总数动态计算每批 embedding 调用的 chunk 数 */
  static int calcDynamicBatchSize(int totalChunks) {
    if (totalChunks < 1_000) return 50;
    if (totalChunks < 5_000) return 100;
    return 200;
  }

  /** 根据 chunk 总数动态计算并行线程数 */
  static int calcParallelism(int totalChunks) {
    if (totalChunks < 500) return 1;
    if (totalChunks < 2_000) return 2;
    return 4;
  }

  // ---------- ES 索引 settings 控制 ----------

  /** 入库前关闭 refresh 和副本，写入吞吐可提升 3-5 倍 */
  private void disableRefreshAndReplicas() {
    try {
      String body = "{\"index\":{\"refresh_interval\":\"-1\",\"number_of_replicas\":0}}";
      Request req = new Request("PUT", "/" + INDEX_NAME + "/_settings");
      req.setJsonEntity(body);
      restClient.performRequest(req);
      log.info("ES 索引 {} 已关闭 refresh 和副本（批量入库优化）", INDEX_NAME);
    } catch (Exception e) {
      log.warn("ES 关闭 refresh/副本失败（不影响主流程，仅写入性能略降）: {}", e.getMessage());
    }
  }

  /** 入库完成后恢复 refresh 间隔和副本数 */
  private void enableRefreshAndReplicas() {
    try {
      String body = "{\"index\":{\"refresh_interval\":\"1s\",\"number_of_replicas\":1}}";
      Request req = new Request("PUT", "/" + INDEX_NAME + "/_settings");
      req.setJsonEntity(body);
      restClient.performRequest(req);
      // 手动 flush 一次保证入库后立即可检索
      Request flushReq = new Request("POST", "/" + INDEX_NAME + "/_flush");
      restClient.performRequest(flushReq);
      log.info("ES 索引 {} 已恢复 refresh=1s, replicas=1 并 flush", INDEX_NAME);
    } catch (Exception e) {
      log.warn("ES 恢复 refresh/副本失败（不影响数据，需手动恢复，已入库数据稍后可查）: {}", e.getMessage());
    }
  }

  // ---------- 删除 ----------

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

  // ---------- 入库 ----------

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
   * <p>优化策略：
   * <ul>
   *   <li>入库前关闭 ES refresh 和副本 → 写入吞吐 3-5x 提升
   *   <li>根据 chunk 总数动态调整 batch size（50/100/200）→ 减少 embedding API 调用次数
   *   <li>大文件多线程并行（2-4 路）→ embedding API 调用并行化，打破串行瓶颈
   *   <li>自适应限速（类似 TCP 拥塞控制）→ 每线程独立调整，自动收敛到最优速率
   *   <li>入库后恢复 ES refresh + flush → 保证新数据立即可检索
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
    int batchSize = calcDynamicBatchSize(total);
    int batches = (total + batchSize - 1) / batchSize;
    int parallelism = calcParallelism(total);

    log.info("ES 分批入库: {} 段, {} 批(每批{}段), {} 路并行, 来源: {}",
        total, batches, batchSize, parallelism, source);

    // 入库前关闭 refresh 和副本
    disableRefreshAndReplicas();
    try {
      if (parallelism <= 1 || total <= batchSize) {
        return sequentialImport(documents, total, batchSize, batches, source, progressCallback);
      }
      return parallelImport(documents, total, batchSize, batches, parallelism, source, progressCallback);
    } finally {
      enableRefreshAndReplicas();
    }
  }

  // ---------- 串行入库 ----------

  private int sequentialImport(List<Document> documents, int total, int batchSize,
                                 int batches, String source, IntConsumer progressCallback) {
    long pauseMs = 0;
    for (int i = 0; i < total; i += batchSize) {
      int end = Math.min(i + batchSize, total);
      List<Document> batch = documents.subList(i, end);
      int batchNo = i / batchSize + 1;

      pauseMs = processOneBatch(batch, batchNo, batches, source, pauseMs);

      if (progressCallback != null) {
        progressCallback.accept(end);
      }
    }
    log.info("ES 串行入库完成: {} 段, 来源: {}", total, source);
    return total;
  }

  // ---------- 并行入库 ----------

  private int parallelImport(List<Document> documents, int total, int batchSize,
                               int batches, int parallelism, String source,
                               IntConsumer progressCallback) {
    AtomicInteger completedCount = new AtomicInteger(0);
    AtomicReference<Exception> firstError = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(parallelism);
    int lastReported = 0; // 用于回调去重（single-thread access on main thread is fine）

    for (int t = 0; t < parallelism; t++) {
      final int threadId = t;
      new Thread(() -> {
        try {
          long pauseMs = 0;
          for (int start = threadId * batchSize; start < total; start += batchSize * parallelism) {
            int end = Math.min(start + batchSize, total);
            List<Document> batch = documents.subList(start, end);
            int batchNo = start / batchSize + 1;

            pauseMs = processOneBatch(batch, batchNo, batches, source, pauseMs);

            int completed = completedCount.addAndGet(batch.size());
            if (progressCallback != null) {
              synchronized (progressCallback) {
                progressCallback.accept(completed);
              }
            }

            if (firstError.get() != null) return;
          }
        } catch (Exception e) {
          firstError.compareAndSet(null, e);
        } finally {
          latch.countDown();
        }
      }, "es-import-" + threadId).start();
    }

    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("ES 并行入库被中断", e);
    }

    Exception err = firstError.get();
    if (err != null) {
      if (err instanceof RuntimeException re) throw re;
      throw new RuntimeException("ES 并行入库失败", err);
    }

    log.info("ES 并行入库完成: {} 段, {} 路并行, 来源: {}", total, parallelism, source);
    return total;
  }

  // ---------- 单批处理（embed + ES add + 自适应限速）----------

  /**
   * 处理一批 chunk：自适应暂停 → vectorStore.add（内部完成 embedding + ES bulk index）→ 根据结果调整暂停。
   *
   * @return 更新后的 pauseMs
   */
  private long processOneBatch(List<Document> batch, int batchNo, int totalBatches,
                                String source, long pauseMs) {
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
          long wait = RETRY_BASE_MS * (1L << attempt);
          log.warn("ES 批次 {}/{} 触发 429 限流, 第 {} 次重试, 等待 {}ms（当前限速 {}ms）",
              batchNo, totalBatches, attempt + 1, wait, pauseMs);
          try { Thread.sleep(wait); } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ES 入库被中断", ie);
          }
        } else {
          throw new RuntimeException("ES 批次 " + batchNo + "/" + totalBatches + " 入库失败", ex);
        }
      }
    }

    // 自适应调整暂停时间
    long newPauseMs;
    if (hit429) {
      newPauseMs = Math.min(pauseMs + PAUSE_INCREASE_MS, PAUSE_MAX_MS);
    } else {
      newPauseMs = Math.max(pauseMs - PAUSE_DECREASE_MS, 0);
    }

    if (batchNo % 20 == 0 || hit429) {
      log.info("ES 批次 {}/{} 完成（{} 段）, 限速: {}ms", batchNo, totalBatches, batch.size(), newPauseMs);
    }
    return newPauseMs;
  }

  // ---------- 工具方法 ----------

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
