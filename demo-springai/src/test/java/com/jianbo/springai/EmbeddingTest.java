package com.jianbo.springai;

import com.jianbo.springai.service.save.EmbeddingService;
import com.jianbo.springai.service.save.VectorStoreService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Embedding + 向量入库 完整测试
 *
 * 运行前确保：
 *   1. MiniMax API Key 已配置到环境变量或yaml
 *   2. PostgreSQL + pgvector 已启动
 *   3. vector_store 表已创建（见第九节建表SQL）
 */
@SpringBootTest
public class EmbeddingTest {

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private VectorStoreService vectorStoreService;

    /**
     * 测试1：单条文本向量化
     */
    @Test
    void testSingleEmbed() {
        String text = "Java是世界上最流行的编程语言之一";
        float[] vector = embeddingService.embed(text);

        System.out.println("========== 单条文本向量化 ==========");
        System.out.println("文本: " + text);
        System.out.println("向量维度: " + vector.length);
        System.out.println("前5维: " + vectorPreview(vector, 5));
    }

    /**
     * 测试2：批量文本向量化
     */
    @Test
    void testBatchEmbed() {
        List<String> chunks = List.of(
                "Java是一种面向对象的编程语言",
                "Python适合数据分析和机器学习",
                "Redis是高性能的内存数据库",
                "Spring是Java最流行的企业级框架"
        );
        List<float[]> vectors = embeddingService.embedBatch(chunks);

        System.out.println("========== 批量文本向量化 ==========");
        System.out.println("输入文本数: " + chunks.size());
        System.out.println("输出向量数: " + vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            System.out.println("片段" + i + " 维度: " + vectors.get(i).length);
        }
    }

    /**
     * 测试3：语义相似度验证
     */
    @Test
    void testSimilarity() {
        System.out.println("========== 语义相似度测试 ==========");

        // 相似文本（预期 > 0.8）
        float sim1 = embeddingService.cosineSimilarity("Java编程", "Java开发");
        System.out.println("Java编程 vs Java开发: " + sim1);

        // 同类文本（预期 0.5~0.8）
        float sim2 = embeddingService.cosineSimilarity("Java编程", "Python编程");
        System.out.println("Java编程 vs Python编程: " + sim2);

        // 无关文本（预期 < 0.5）
        float sim3 = embeddingService.cosineSimilarity("Java编程", "今天天气很好");
        System.out.println("Java编程 vs 今天天气很好: " + sim3);
    }

    /**
     * 测试4：文档完整入库（切片+向量化+pgvector）
     */
    @Test
    void testImportDocument() {
//        String content = """
//            Java是一门面向对象的编程语言，由Sun公司于1995年发布。
//            Java具有跨平台、安全性高、面向对象、多线程等特性。
//            Java广泛应用于企业级应用、Android开发、大数据等领域。
//            JVM是Java虚拟机，是Java跨平台的核心实现。
//            Spring是Java最流行的企业级开发框架，简化了Java开发。
//            MyBatis是Java生态中的持久层框架，用于数据库操作。
//            Redis是一款高性能的内存数据库，常用于缓存和分布式锁。
//            PostgreSQL是强大的关系型数据库，配合pgvector可做向量检索。
//            """;
        String content = """
2023年9月20日,以“虚实相生,产业赋能”为主题的WMC2023第二届世界元宇宙大会在上海安亭隆重举行。
大会由中国仿真学会、中国指挥与控制学会和北京理工大学共同主办,上海市嘉定区安亭镇人民政府和中国仿真学会元宇宙专业委员会共同承办。
工业和信息化部科技司副司长任爱光、中国仿真学会副理事长纪志成、北京理工大学计算机学院党委书记丁刚毅、上海市嘉定区委副书记、区长高香出席开幕式并致辞。
""";

        int count = vectorStoreService.importDocument(content, "java_knowledge.txt");

        System.out.println("========== 文档入库完成 ==========");
        System.out.println("入库切片数: " + count);
    }

    // ========== 辅助方法 ==========

    private String vectorPreview(float[] vector, int limit) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(limit, vector.length); i++) {
            sb.append(String.format("%.4f", vector[i]));
            if (i < limit - 1) sb.append(", ");
        }
        sb.append(", ...]");
        return sb.toString();
    }
}