package com.jianbo.springai.controller;

import com.jianbo.springai.service.search.EsVectorSearchService;
import com.jianbo.springai.service.search.VectorSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 向量检索测试接口
 *
 * 测试地址（端口 12115）：
 *   PG 简单检索：   GET  http://localhost:12115/search/pg?query=JVM是什么
 *   PG 指定TopK：  GET  http://localhost:12115/search/pg?query=Java&topK=3
 *   PG 完整配置：   GET  http://localhost:12115/search/pg/full?query=Spring&topK=5&threshold=0.7
 *   PG 按来源：    GET  http://localhost:12115/search/pg/source?query=Redis&source=java.pdf&topK=3
 *   ES 检索：     GET  http://localhost:12115/search/es?query=JVM调优
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {


    private final VectorSearchService vectorSearchService;
    private final EsVectorSearchService esVectorSearchService;

    // ==================== PG 检索接口 ====================
    /**
     * PG 语义检索（支持可选 topK）
     * GET /search/pg?query=JVM是什么
     * GET /search/pg?query=Java&topK=3
     *
     * 注意：两个路径都是 /pg，Spring MVC 不允许重复映射！
     * 合并为一个方法，topK 用 defaultValue 处理
     */
    @GetMapping("/pg")
    public Map<String, Object> pgSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = vectorSearchService.search(query, topK);

        return Map.of(
                "store", "pgvector",
                "query", query,
                "topK", topK,
                "count", results.size(),
                "results", formatResults(results)
        );
    }

    /**
     * 完整配置检索
     * GET /search/pg/full?query=Spring&topK=5&threshold=0.7
     */
    @GetMapping("/pg/full")
    public Map<String, Object> pgSearchFull(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "0.7") double threshold) {
        List<Document> results = vectorSearchService.search(query, topK, threshold);

        return Map.of(
                "store", "pgvector",
                "query", query,
                "topK", topK,
                "threshold", threshold,
                "count", results.size(),
                "results", formatResults(results)
        );
    }

    /**
     * 按来源过滤检索
     * GET /search/pg/source?query=Redis&source=java.pdf&topK=3
     */
    @GetMapping("/pg/source")
    public Map<String, Object> pgSearchWithSource(
            @RequestParam String query,
            @RequestParam String source,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = vectorSearchService.search(query, source, topK);

        return Map.of(
                "store", "pgvector",
                "query", query,
                "source", source,
                "topK", topK,
                "count", results.size(),
                "results", formatResults(results)
        );
    }

    // ==================== ES 检索接口 ====================

    /**
     * ES 语义检索
     * GET /search/es?query=JVM调优
     */
    @GetMapping("/es")
    public Map<String, Object> esSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = esVectorSearchService.search(query, topK);
        return Map.of(
                "store", "elasticsearch",
                "query", query,
                "topK", topK,
                "count", results.size(),
                "results", formatResults(results)
        );
    }

    // ==================== 辅助方法 ====================

    /**
     * 格式化检索结果（便于展示）
     */
    private List<Map<String, Object>> formatResults(List<Document> results) {
        return results.stream()
                .map(doc -> {
                    if (doc.getText() != null) {
                        return Map.of(
                                "id", doc.getId(),
                                "content", doc.getText(),
                                "source", doc.getMetadata().getOrDefault("source", "unknown"),
                                "chunkIndex", doc.getMetadata().getOrDefault("chunk_index", "0")
                        );
                    }
                    return null;
                }) .collect(Collectors.toList());
    }
}
