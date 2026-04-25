package com.jianbo.springai;

import com.jianbo.springai.service.search.VectorSearchService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量检索完整测试
 *
 * 运行前确保：
 *   1. Day24 的入库测试已执行（vector_store 表有数据）
 *   2. PostgreSQL + pgvector 已启动
 *   3. MiniMax API Key 已配置
 */
@SpringBootTest
public class VectorSearchTest {

    @Resource
    private VectorSearchService vectorSearchService;

    /**
     * 测试1：简单语义检索
     */
    @Test
    void testSimpleSearch() {
        System.out.println("========== 简单语义检索 ==========");

        List<Document> results = vectorSearchService.search("JVM内存模型");

        System.out.println("问题：JVM内存模型");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试2：指定 TopK 检索
     */
    @Test
    void testSearchWithTopK() {
        System.out.println("========== 指定 TopK 检索 ==========");

        List<Document> results = vectorSearchService.search("Java编程", 3);

        System.out.println("问题：Java编程，TopK=3");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试3：带阈值的检索
     */
    @Test
    void testSearchWithThreshold() {
        System.out.println("========== 带阈值的检索 ==========");

        // threshold=0.9 非常严格，应该召回很少
        List<Document> results = vectorSearchService.search("Java", 5, 0.9);

        System.out.println("问题：Java，TopK=5，threshold=0.9（严格）");
        System.out.println("召回数量：" + results.size());
        printResults(results);

        // threshold=0.0 不过滤
        List<Document> results2 = vectorSearchService.search("Java", 5, 0.0);
        System.out.println("\n问题：Java，TopK=5，threshold=0.0（不过滤）");
        System.out.println("召回数量：" + results2.size());
        printResults(results2);
    }

    /**
     * 测试4：按来源过滤检索
     */
    @Test
    void testSearchWithSourceFilter() {
        System.out.println("========== 按来源过滤检索 ==========");

        // 只查 redis_guide.txt 来源的文档
        List<Document> results = vectorSearchService.search(
                "缓存", "redis_guide.txt", 3);

        System.out.println("问题：缓存，来源：redis_guide.txt");
        System.out.println("召回数量：" + results.size());
        printResults(results);
    }

    /**
     * 测试5：语义相近但用词不同（验证语义检索能力）
     */
    @Test
    void testSemanticSearch() {
        System.out.println("========== 语义检索能力验证 ==========");

        // 问："堆和栈有什么区别"
        // 库中存的是："虚拟机栈与堆内存差异"
        List<Document> results = vectorSearchService.search("堆和栈有什么区别", 3);

        System.out.println("问题：堆和栈有什么区别");
        System.out.println("（库中存的是：虚拟机栈与堆内存差异）");
        System.out.println("召回数量：" + results.size());
        printResults(results);

        // 对比 MySQL like 查询（肯定搜不到）
        System.out.println("\n对比：MySQL LIKE 搜 '堆和栈有什么区别' --> 0 条");
    }

    // ========== 辅助方法 ==========

    private void printResults(List<Document> results) {
        for (int i = 0; i < results.size(); i++) {
            Document doc = results.get(i);
            System.out.println("--- 第" + (i + 1) + "条 ---");
            System.out.println("ID: " + doc.getId());
            System.out.println("来源: " + doc.getMetadata().get("source"));
            System.out.println("片段: " + doc.getMetadata().get("chunk_index"));
            System.out.println("内容: " + doc.getContent());
        }
    }
}