package com.jianbo.localaiknowledge.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Elasticsearch 向量入库服务（对标 Spring AI 版 EsVectorStoreService）。
 *
 * <h2>核心差异</h2>
 * <pre>
 * Spring AI:
 *   vectorStore.add(documents);    // 一步完成：embed + store
 *
 * LangChain4j:
 *   List&lt;Embedding&gt; embeddings = embeddingModel.embedAll(segments).content();
 *   embeddingStore.addAll(embeddings, segments);  // 手动两步
 * </pre>
 *
 * <p>自适应限速策略与原版一致（类 TCP 拥塞控制）。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorStoreService {

    private final ElasticsearchEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;

    private static final int EMBED_BATCH_SIZE = 50;
    private static final int MAX_RETRY = 5;
    private static final long RETRY_BASE_MS = 3000;
    private static final long PAUSE_INCREASE_MS = 2000;
    private static final long PAUSE_DECREASE_MS = 500;
    private static final long PAUSE_MAX_MS = 8000;

    public int importChunks(List<String> chunks, String source, String userId, String docScope) {
        return importChunks(chunks, source, userId, docScope, null);
    }

    public int importChunks(List<String> chunks, String source, String userId, String docScope,
                            IntConsumer progressCallback) {
        if (chunks == null || chunks.isEmpty()) return 0;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = Metadata.from("source", source)
                    .put("chunk_index", String.valueOf(i))
                    .put("total_chunks", String.valueOf(chunks.size()))
                    .put("doc_scope", docScope != null ? docScope : "PUBLIC");
            if (userId != null) metadata.put("user_id", userId);
            segments.add(TextSegment.from(chunks.get(i), metadata));
        }

        int total = segments.size();
        int batches = (total + EMBED_BATCH_SIZE - 1) / EMBED_BATCH_SIZE;
        log.info("ES 分批入库: 共 {} 段, 分 {} 批, 来源: {}", total, batches, source);

        long pauseMs = 0;
        for (int i = 0; i < total; i += EMBED_BATCH_SIZE) {
            int end = Math.min(i + EMBED_BATCH_SIZE, total);
            List<TextSegment> batch = segments.subList(i, end);
            int batchNo = i / EMBED_BATCH_SIZE + 1;

            if (pauseMs > 0) {
                try { Thread.sleep(pauseMs); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("ES 入库被中断", ie);
                }
            }

            boolean hit429 = false;
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                try {
                    // LangChain4j: 手动 embed + store（Spring AI 版是 vectorStore.add 一步到位）
                    Response<List<Embedding>> embResp = embeddingModel.embedAll(batch);
                    embeddingStore.addAll(embResp.content(), batch);
                    break;
                } catch (Exception ex) {
                    if (attempt < MAX_RETRY && is429(ex)) {
                        hit429 = true;
                        long wait = RETRY_BASE_MS * (1L << attempt);
                        log.warn("ES 批次 {}/{} 触发 429, 重试 {}, 等 {}ms", batchNo, batches, attempt + 1, wait);
                        try { Thread.sleep(wait); } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("ES 入库被中断", ie);
                        }
                    } else {
                        throw ex;
                    }
                }
            }

            pauseMs = hit429 ? Math.min(pauseMs + PAUSE_INCREASE_MS, PAUSE_MAX_MS)
                             : Math.max(pauseMs - PAUSE_DECREASE_MS, 0);

            log.info("ES 批次 {}/{} 完成（{} 段）, 限速: {}ms", batchNo, batches, batch.size(), pauseMs);
            if (progressCallback != null) progressCallback.accept(end);
        }
        log.info("ES 入库完成: {} 段, 来源: {}", total, source);
        return total;
    }

    private static boolean is429(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("429")) return true;
        }
        return false;
    }
}
