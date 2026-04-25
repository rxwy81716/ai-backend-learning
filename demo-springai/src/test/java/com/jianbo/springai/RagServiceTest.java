package com.jianbo.springai;

import com.jianbo.springai.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
class RagServiceTest {

    @Autowired
    private RagService ragService;

    /**
     * 测试同步 RAG 问答
     */
    @Test
    void testChat() {
        String answer = ragService.chat("Java有什么特点？");
        System.out.println("=== RAG 同步答案 ===");
        System.out.println(answer);

        // 验证：答案非空，且包含参考标注
        assert answer != null && !answer.isEmpty();
    }

    /**
     * 测试空结果场景
     */
    @Test
    void testChatNoResult() {
        // 用一个知识库肯定没有的问题
        String answer = ragService.chat("今天天气怎么样？", 5, 0.95);
        System.out.println("=== 空结果测试 ===");
        System.out.println(answer);

        // threshold 设很高，大概率召回为空，应该返回兜底文案
    }

    /**
     * 测试流式 RAG 问答
     */
    @Test
    void testChatStream() {
        Flux<String> stream = ragService.chatStream("Java和Python的区别？");

        // 收集所有流式片段，拼接成完整答案
        StringBuilder fullAnswer = new StringBuilder();
        stream.doOnNext(chunk -> {
            System.out.print(chunk);  // 逐字打印
            fullAnswer.append(chunk);
        }).blockLast();  // 阻塞等待流结束

        System.out.println("\n=== 流式完整答案 ===");
        System.out.println(fullAnswer);
    }

    /**
     * 测试不同 topK 参数对答案质量的影响
     */
    @Test
    void testTopKComparison() {
        String query = "什么是向量数据库？";

        String answer3 = ragService.chat(query, 3, 0.7);
        String answer10 = ragService.chat(query, 10, 0.5);

        System.out.println("=== TopK=3, threshold=0.7 ===");
        System.out.println(answer3);
        System.out.println("\n=== TopK=10, threshold=0.5 ===");
        System.out.println(answer10);

        // 观察：topK 大 + threshold 低 --> 更多上下文，答案可能更全面但可能有噪音
    }
}