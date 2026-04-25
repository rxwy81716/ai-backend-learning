package com.jianbo.springai.controller;

import com.jianbo.springai.service.save.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 向量存储测试接口
 *
 * 测试地址（端口 12115）：
 *   PG VectorStore入库：POST http://localhost:12115/vector/pg/import?source=test.txt
 *   PG JDBC手动入库：  POST http://localhost:12115/vector/pg/jdbc?title=Java基础&source=test.txt
 *   查询入库数量：     GET  http://localhost:12115/vector/pg/count
 */
@RestController
@RequestMapping("/vector")
@RequiredArgsConstructor
public class VectorStoreController {
    private final VectorStoreService vectorStoreService;

    /**
     * PG VectorStore 方式入库
     * POST /vector/pg/import?source=java_guide.pdf
     * Body: 文档全文
     */
    @PostMapping("/pg/import")
    public Map<String, Object> pgImport(
            @RequestBody String content,
            @RequestParam(defaultValue = "test.txt") String source) {
        int count = vectorStoreService.importDocument(content, source);
        return Map.of("store", "pgvector", "chunks", count, "status", "success");
    }

    /**
     * PG JDBC 手动入库
     * POST /vector/pg/jdbc?title=Java基础&source=java.pdf
     * Body: ["片段1", "片段2", ...]
     */
    @PostMapping("/pg/jdbc")
    public Map<String, Object> pgJdbc(
            @RequestBody java.util.List<String> chunks,
            @RequestParam String title,
            @RequestParam(defaultValue = "manual") String source) {
        int count = vectorStoreService.importChunks(title, source, chunks);
        return Map.of("store", "pgvector_jdbc", "chunks", count, "status", "success");
    }
}
