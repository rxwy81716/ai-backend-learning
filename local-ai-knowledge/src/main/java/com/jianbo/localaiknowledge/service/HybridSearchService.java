package com.jianbo.localaiknowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 混合检索服务：向量召回 + BM25 关键词召回 + RRF 融合
 *
 * <p>RRF (Reciprocal Rank Fusion) 公式：
 * <pre>
 *   score(d) = Σ  1 / (k + rank_i(d))
 * </pre>
 * 其中 rank_i 为文档 d 在第 i 路召回结果中的排名（从 1 开始），k 为平滑常数（默认 60）。
 *
 * <p>优势：
 * <ul>
 *   <li>不依赖 score 量纲（向量 cosine vs BM25 完全不同尺度）</li>
 *   <li>对单路漏召的文档容错（只要另一路命中就能进 topK）</li>
 *   <li>实现极简，工程稳定</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final EsVectorSearchService vectorSearchService;
    private final EsKeywordSearchService keywordSearchService;

    @Value("${app.rag.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${app.rag.hybrid.vector-top-k:30}")
    private int vectorTopK;

    @Value("${app.rag.hybrid.keyword-top-k:30}")
    private int keywordTopK;

    @Value("${app.rag.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${app.rag.hybrid.parallel-timeout-ms:3000}")
    private long parallelTimeoutMs;

    @Value("${app.rag.hybrid.similarity-threshold:0.25}")
    private double similarityThreshold;

    /**
     * 混合检索（带用户归属过滤）
     *
     * @param query  用户问题
     * @param userId 用户 ID（null = 只看公共文档）
     * @param topK   最终返回数量
     * @return 融合排序后的 topK 文档（metadata 中含 hybrid_score / vector_rank / bm25_rank）
     */
    public List<Document> searchWithOwnership(String query, String userId, int topK) {
        // 关闭混合检索 → 直接走纯向量
        if (!hybridEnabled) {
            return vectorSearchService.searchWithOwnership(query, userId, topK, similarityThreshold);
        }

        long t0 = System.currentTimeMillis();

        // 并发跑两路召回
        CompletableFuture<List<Document>> vectorFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return vectorSearchService.searchWithOwnership(query, userId, vectorTopK, similarityThreshold);
            } catch (Exception e) {
                log.warn("向量检索失败，降级 | err={}", e.getMessage());
                return List.<Document>of();
            }
        });

        CompletableFuture<List<Document>> keywordFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return keywordSearchService.searchWithOwnership(query, userId, keywordTopK);
            } catch (Exception e) {
                log.warn("BM25 检索失败，降级 | err={}", e.getMessage());
                return List.<Document>of();
            }
        });

        List<Document> vectorHits;
        List<Document> keywordHits;
        try {
            CompletableFuture<Void> all = CompletableFuture.allOf(vectorFuture, keywordFuture);
            all.get(parallelTimeoutMs, TimeUnit.MILLISECONDS);
            vectorHits = vectorFuture.getNow(List.of());
            keywordHits = keywordFuture.getNow(List.of());
        } catch (TimeoutException te) {
            log.warn("混合检索整体超时 {}ms，使用已完成的部分结果", parallelTimeoutMs);
            vectorHits = vectorFuture.getNow(List.of());
            keywordHits = keywordFuture.getNow(List.of());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return vectorSearchService.searchWithOwnership(query, userId, topK, similarityThreshold);
        } catch (ExecutionException ee) {
            log.warn("混合检索执行异常，降级到纯向量 | err={}", ee.getMessage());
            return vectorSearchService.searchWithOwnership(query, userId, topK, similarityThreshold);
        }

        long t1 = System.currentTimeMillis();
        List<Document> fused = rrfFuse(vectorHits, keywordHits, topK);
        long t2 = System.currentTimeMillis();

        log.info("⏱ Hybrid检索 vector={}条/{}ms, bm25={}条, RRF融合={}条/{}ms, 总{}ms",
                vectorHits.size(), t1 - t0, keywordHits.size(), fused.size(), t2 - t1, t2 - t0);

        return fused;
    }

    /**
     * RRF 融合多路召回结果
     */
    private List<Document> rrfFuse(List<Document> vectorHits, List<Document> keywordHits, int topK) {
        // docId -> 累计 RRF 分数
        Map<String, Double> rrfScores = new HashMap<>();
        // docId -> 原始 Document（取首次出现的）
        Map<String, Document> docMap = new LinkedHashMap<>();
        // 用于 metadata 标注排名
        Map<String, Integer> vectorRank = new HashMap<>();
        Map<String, Integer> bm25Rank = new HashMap<>();

        accumulate(vectorHits, rrfScores, docMap, vectorRank);
        accumulate(keywordHits, rrfScores, docMap, bm25Rank);

        // 按 RRF 分数倒序排
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Document> result = new ArrayList<>(Math.min(topK, sorted.size()));
        for (int i = 0; i < Math.min(topK, sorted.size()); i++) {
            String id = sorted.get(i).getKey();
            Document origin = docMap.get(id);

            // 写入融合元数据，便于调试 / 引用展示
            Map<String, Object> meta = new HashMap<>(origin.getMetadata());
            meta.put("hybrid_score", sorted.get(i).getValue());
            if (vectorRank.containsKey(id)) meta.put("vector_rank", vectorRank.get(id));
            if (bm25Rank.containsKey(id)) meta.put("bm25_rank", bm25Rank.get(id));

            result.add(new Document(origin.getId(), origin.getText(), meta));
        }
        return result;
    }

    private void accumulate(List<Document> hits, Map<String, Double> rrfScores,
                            Map<String, Document> docMap, Map<String, Integer> rankMap) {
        for (int i = 0; i < hits.size(); i++) {
            Document doc = hits.get(i);
            String id = doc.getId();
            if (id == null || id.isBlank()) continue;

            int rank = i + 1;
            double contribution = 1.0 / (rrfK + rank);
            rrfScores.merge(id, contribution, Double::sum);
            docMap.putIfAbsent(id, doc);
            rankMap.putIfAbsent(id, rank);
        }
    }
}
