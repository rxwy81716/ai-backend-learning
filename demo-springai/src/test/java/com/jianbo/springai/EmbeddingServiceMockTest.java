package com.jianbo.springai;

import com.jianbo.springai.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmbeddingServiceMockTest {

    /**
     * @Mock 创建一个假的 EmbeddingModel 对象
     * 它的所有方法默认返回 null/0/false
     */
    @Mock
    private EmbeddingModel embeddingModel;

    /**
     * @InjectMocks 创建 EmbeddingService 真实对象
     * 并把上面的 @Mock 自动注入进去
     */
    @InjectMocks
    private EmbeddingService embeddingService;


    @Test
    void testEmbed() {
        // ===== 1. 准备假数据：1536维全零向量 =====
        float[] fakeVector = new float[1536];
        Embedding fakeEmbedding = new Embedding(fakeVector, 0);
        EmbeddingResponse fakeResponse =
                new EmbeddingResponse(List.of(fakeEmbedding));

        // ===== 2. 规定 mock 行为：调用 call() 时返回假响应 =====
        // when(假对象.方法(任意参数)).thenReturn(假数据)
        when(embeddingModel.call(any(EmbeddingRequest.class)))
                .thenReturn(fakeResponse);

        // ===== 3. 调用真实方法（内部会调 mock，不会真实请求 MiniMax）=====
        float[] result = embeddingService.embed("Java是什么");

        // ===== 4. 断言结果 =====
        assertEquals(1536, result.length);

        // ===== 5. 验证 mock 方法确实被调用了一次 =====
        verify(embeddingModel, times(1)).call(any(EmbeddingRequest.class));
    }
}
