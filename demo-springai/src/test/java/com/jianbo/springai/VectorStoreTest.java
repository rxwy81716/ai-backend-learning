package com.jianbo.springai;

import com.jianbo.springai.service.save.VectorStoreService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 向量存储测试
 *
 * 运行前确保：
 *   1. PostgreSQL + pgvector 已启动
 *   2. vector_store / my_documents 表已创建
 *   3. MiniMax API Key（按量计费 sk-api-）已配置
 */
@SpringBootTest
public class VectorStoreTest {
    @Resource
    private VectorStoreService vectorStoreService;

    /**
     * 测试1：VectorStore 方式入库（推荐）
     */
    @Test
    void testVectorStoreImport() {
        String content = """
            Java是一门面向对象的编程语言，由Sun公司于1995年发布。
            Java具有跨平台、安全性高、多线程等特性。
            JVM是Java虚拟机，是实现跨平台的核心。
            Spring是Java最流行的企业级框架。
            Spring Boot简化了Spring应用的配置和部署。
            MyBatis是Java生态中的持久层框架。
            Redis常用于缓存和分布式锁。
            PostgreSQL配合pgvector可做向量检索。
            """;

        int count = vectorStoreService.importDocument(content, "java_knowledge.txt");

        System.out.println("========== VectorStore 入库 ==========");
        System.out.println("入库切片数: " + count);
        System.out.println("验证SQL: SELECT count(*) FROM vector_store;");
    }

    /**
     * 测试2：JDBC 手动入库
     */
    @Test
    void testJdbcImport() {
        var chunks = java.util.List.of(
                "Java是一种面向对象的编程语言",
                "Python适合数据分析和机器学习",
                "Redis是高性能的内存数据库"
        );

        int count = vectorStoreService.importChunks("编程语言入门", "test.txt", chunks);

        System.out.println("========== JDBC手动入库 ==========");
        System.out.println("入库切片数: " + count);
        System.out.println("验证SQL: SELECT count(*) FROM my_documents;");
    }
}
