package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;
import com.jianbo.localaiknowledge.utils.SimpleCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 混合检索服务（对标 Spring AI 版 HybridSearchService）。
 *
 * <h2>与 Spring AI 版的差异</h2>
 * <p>返回类型从 {@code List<Document>} 改为 {@code List<SearchDoc>}，
 * 是对 LangChain4j {@code EmbeddingMatch<TextSegment>} 的封装。
 * RRF 融合逻辑、熔断器、Rerank 调用完全一致。</p>
 */
@Service
@Slf4j
public class HybridSearchService {

    private final EsVectorSearchService vectorSearchService;
    private final EsKeywordSearchService keywordSearchService;
    private final RerankService rerankService;
    private final SimpleCircuitBreaker circuitBreaker;

    public HybridSearchService(EsVectorSearchService vectorSearchService,
                                EsKeywordSearchService keywordSearchService,
                                RerankService rerankService) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.rerankService = rerankService;
        this.circuitBreaker = new SimpleCircuitBreaker(3, 30_000);
    }

    public List<SearchDoc> searchWithOwnership(String query, String userId, int topK) {
        try {
            return circuitBreaker.call(() -> doSearch(query, userId, topK));
        } catch (Exception e) {
            log.error("[HybridSearch] 熔断后降级: {}", e.getMessage());
            return vectorSearchService.searchWithOwnership(query, userId, topK);
        }
    }

    private List<SearchDoc> doSearch(String query, String userId, int topK) {
        // 1. 并行执行向量 + BM25
        var vectorFuture = CompletableFuture.supplyAsync(() ->
                vectorSearchService.searchWithOwnership(query, userId, topK * 2));
        var keywordFuture = CompletableFuture.supplyAsync(() ->
                keywordSearchService.search(query, userId, topK * 2));

        List<SearchDoc> vectorDocs = vectorFuture.join();
        List<SearchDoc> keywordDocs = keywordFuture.join();

        // 2. RRF 融合
        List<SearchDoc> fused = rrfFusion(vectorDocs, keywordDocs, topK * 2);

        // 3. Rerank
        if (rerankService.isEnabled() && !fused.isEmpty()) {
            fused = rerankService.rerank(query, fused, topK);
        } else if (fused.size() > topK) {
            fused = fused.subList(0, topK);
        }

        return fused;
    }

    /** RRF 融合排序（算法与 Spring AI 版完全一致） */
    private List<SearchDoc> rrfFusion(List<SearchDoc> vectorDocs, List<SearchDoc> keywordDocs, int limit) {
        int k = 60; // RRF 常数
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchDoc> docMap = new LinkedHashMap<>();

        for (int i = 0; i < vectorDocs.size(); i++) {
            SearchDoc doc = vectorDocs.get(i);
            String key = doc.content().hashCode() + "";
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            docMap.putIfAbsent(key, doc);
        }
        for (int i = 0; i < keywordDocs.size(); i++) {
            SearchDoc doc = keywordDocs.get(i);
            String key = doc.content().hashCode() + "";
            scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
            docMap.putIfAbsent(key, doc);
        }

        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(limit)
                .map(e -> docMap.get(e.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
