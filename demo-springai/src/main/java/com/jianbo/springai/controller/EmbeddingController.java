package com.jianbo.springai.controller;

import com.jianbo.springai.service.save.EmbeddingService;
import com.jianbo.springai.service.save.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding + 向量入库 测试接口
 *
 * 测试地址（项目端口 12115）：
 *   单条向量化：GET  http://localhost:12115/embedding/single?text=Java是什么
 *   相似度：   GET  http://localhost:12115/embedding/similarity?t1=Java&t2=Python
 *   批量向量化：POST http://localhost:12115/embedding/batch
 *   文档入库：  POST http://localhost:12115/embedding/import?source=test.txt
 */
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    /**
     * 测试1：单条文本向量化
     * GET /embedding/single?text=Java是面向对象的编程语言
     */
    @GetMapping("/single")
    public Map<String, Object> singleEmbed(@RequestParam String text) {
        float[] vector = embeddingService.embed(text);

        Map<String, Object> result = new HashMap<>();
        result.put("text", text);
        result.put("dimensions", vector.length);
        result.put("vector_preview",
                Arrays.toString(Arrays.copyOf(vector, 5)) + "...");
        return result;
    }

    /**
     * 测试2：计算两段文本的语义相似度
     * GET /embedding/similarity?t1=Java编程&t2=Python编程
     */
    @GetMapping("/similarity")
    public Map<String, Object> similarity(
            @RequestParam String t1, @RequestParam String t2) {
        float sim = embeddingService.cosineSimilarity(t1, t2);

        Map<String, Object> result = new HashMap<>();
        result.put("text1", t1);
        result.put("text2", t2);
        result.put("similarity", sim);
        result.put("judge", sim > 0.8 ? "高度相似"
                : sim > 0.5 ? "中等相似" : "不太相似");
        return result;
    }

    /**
     * 测试3：批量文本向量化
     * POST /embedding/batch
     * Body: ["Java是编程语言", "Python适合数据分析"]
     */
    @PostMapping("/batch")
    public Map<String, Object> batchEmbed(@RequestBody List<String> texts) {
        List<float[]> vectors = embeddingService.embedBatch(texts);

        Map<String, Object> result = new HashMap<>();
        result.put("input_count", texts.size());
        result.put("output_count", vectors.size());
        result.put("dimensions", vectors.get(0).length);
        return result;
    }

    /**
     * 测试4：文档入库（切片+向量化+存pgvector）
     * POST /embedding/import?source=test_doc.txt
     * Body: "Java是一门面向对象的编程语言...（长文本）"
     */
    @PostMapping("/import")
    public Map<String, Object> importDoc(
            @RequestBody String content,
            @RequestParam(defaultValue = "test_doc.txt") String source) {
        int count = vectorStoreService.importDocument(content, source);

        Map<String, Object> result = new HashMap<>();
        result.put("source", source);
        result.put("chunks_imported", count);
        result.put("status", "success");
        return result;
    }
}