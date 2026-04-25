package com.jianbo.springai;

import com.jianbo.springai.service.save.EmbeddingService;
import com.jianbo.springai.service.save.EsVectorStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ES 向量存储 Mock 测试
 *
 * 纯 Mockito，不启动 Spring，不连 ES，不调 API
 * --> 毫秒级完成，离线可跑，不花钱
 */
@ExtendWith(MockitoExtension.class)
class EsVectorStoreServiceMockTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private org.elasticsearch.client.RestClient restClient;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private EsVectorStoreService esVectorStoreService;

    /**
     * 测试1：VectorStore 方式入库
     * 验证：切片后调用 vectorStore.add()
     */
    @Test
    void testImportDocuments() {
        // 调用
        int count = esVectorStoreService.importDocuments(
                "Java是面向对象语言。Python适合数据分析。", "test.txt");

        // 验证 vectorStore.add() 被调用了一次
        verify(vectorStore, times(1)).add(anyList());
        // 返回的切片数 > 0
        assertTrue(count > 0);
        System.out.println("VectorStore入库切片数: " + count);
    }

    /**
     * 测试2：批量文档入库
     */
    @Test
    void testBatchImport() {
        var docs = java.util.Map.of(
                "doc1.txt", "Java是编程语言",
                "doc2.txt", "Python适合AI"
        );
        int total = esVectorStoreService.importDocuments(docs);

        // vectorStore.add() 被调用 2 次（2个文档）
        verify(vectorStore, times(2)).add(anyList());
        assertTrue(total > 0);
        System.out.println("批量入库总切片数: " + total);
    }

    /**
     * 测试3：手动 RestClient 入库
     * mock embedBatch 返回假向量，验证流程正确
     */
    @Test
    void testManualImportChunks() throws Exception {
        List<String> chunks = List.of("Java是编程语言", "Spring是框架");

        // mock: embedBatch 返回2个假向量
        List<float[]> fakeVectors = List.of(
                new float[1536],
                new float[1536]
        );
        when(embeddingService.embedBatch(chunks)).thenReturn(fakeVectors);

        // mock: objectMapper.writeValueAsString 返回假 JSON
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // mock: restClient.performRequest 返回 mock Response
        org.elasticsearch.client.Response mockResponse = mock(org.elasticsearch.client.Response.class);
        when(restClient.performRequest(any())).thenReturn(mockResponse);

        // 调用
        int count = esVectorStoreService.importChunks("Java基础", "test.txt", chunks);

        // 验证
        assertEquals(2, count);
        // embedBatch 被调用一次
        verify(embeddingService, times(1)).embedBatch(chunks);
        // restClient 被调用 2 次（2个片段逐条写入）
        verify(restClient, times(2)).performRequest(any());
        System.out.println("手动入库: " + count + " 条");
    }

    /**
     * 测试4：删除文档
     */
    @Test
    void testDeleteDocuments() {
        List<String> ids = List.of("id-1", "id-2", "id-3");
        esVectorStoreService.deleteDocuments(ids);

        verify(vectorStore, times(1)).delete(ids);
        System.out.println("删除验证通过");
    }

    /**
     * 测试5：验证 embedBatch 参数内容
     * 用 ArgumentCaptor 抓取传给 embedBatch 的实际参数
     */
    @Test
    void testEmbedBatchCalledWithCorrectChunks() throws Exception {
        List<String> chunks = List.of("测试片段A", "测试片段B");

        when(embeddingService.embedBatch(any())).thenReturn(
                List.of(new float[1536], new float[1536]));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        org.elasticsearch.client.Response mockResponse = mock(org.elasticsearch.client.Response.class);
        when(restClient.performRequest(any())).thenReturn(mockResponse);

        esVectorStoreService.importChunks("测试", "test.txt", chunks);

        // 抓取 embedBatch 的参数
        org.mockito.ArgumentCaptor<List<String>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(embeddingService).embedBatch(captor.capture());

        List<String> captured = captor.getValue();
        assertEquals(2, captured.size());
        assertEquals("测试片段A", captured.get(0));
        System.out.println("参数捕获验证通过: " + captured);
    }
}