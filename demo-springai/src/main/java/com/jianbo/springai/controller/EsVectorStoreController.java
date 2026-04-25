package com.jianbo.springai.controller;

import com.jianbo.springai.service.save.EsVectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 向量存储测试接口
 *
 * 测试地址（端口 12115）：
 *   ES VectorStore入库：POST http://localhost:12115/vector/es/import?source=test.txt
 *   ES 手动入库：       POST http://localhost:12115/vector/es/manual?title=Java基础&source=test.txt
 *   ES 批量入库：       POST http://localhost:12115/vector/es/bulk?title=Java基础&source=test.txt
 *   ES 创建索引：       POST http://localhost:12115/vector/es/create-index
 */
@RestController
@RequestMapping("/vector/es")
@RequiredArgsConstructor
public class EsVectorStoreController {
    private final EsVectorStoreService esVectorStoreService;

    /**
     * ES VectorStore 方式入库（自动 Embedding + 存入 ES）
     * POST /vector/es/import?source=java_guide.pdf
     * Body: 文档全文
     */
    @PostMapping("/import")
    public Map<String, Object> esImport(
            @RequestBody String content,
            @RequestParam(defaultValue = "test.txt") String source) {
        int count = esVectorStoreService.importDocuments(content, source);
        return Map.of("store", "elasticsearch", "chunks", count, "status", "success");
    }

    /**
     * ES 手动 RestClient 入库
     * POST /vector/es/manual?title=Java基础&source=test.txt
     * Body: ["片段1", "片段2"]
     */
    @PostMapping("/manual")
    public Map<String, Object> esManual(
            @RequestBody List<String> chunks,
            @RequestParam String title,
            @RequestParam(defaultValue = "manual") String source) throws IOException {
        int count = esVectorStoreService.importChunks(title, source, chunks);
        return Map.of("store", "es_manual", "chunks", count, "status", "success");
    }

    /**
     * ES 批量 _bulk 入库（性能更高）
     * POST /vector/es/bulk?title=Java基础&source=test.txt
     * Body: ["片段1", "片段2"]
     */
    @PostMapping("/bulk")
    public Map<String, Object> esBulk(
            @RequestBody List<String> chunks,
            @RequestParam String title,
            @RequestParam(defaultValue = "bulk") String source) throws IOException {
        int count = esVectorStoreService.bulkImportDocuments(title, source, chunks);
        return Map.of("store", "es_bulk", "chunks", count, "status", "success");
    }

    /**
     * 手动创建 ES 自定义索引（只需调一次）
     * POST /vector/es/create-index
     */
    @PostMapping("/create-index")
    public Map<String, Object> createIndex() throws IOException {
        esVectorStoreService.createIndex();
        return Map.of("index", "my_documents_es", "status", "created");
    }
}
