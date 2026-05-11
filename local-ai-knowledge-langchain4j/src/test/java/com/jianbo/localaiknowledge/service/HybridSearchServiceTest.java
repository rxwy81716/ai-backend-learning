package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * HybridSearchService 单元测试 — LangChain4j 版。
 *
 * <h3>与 Spring AI 版差异</h3>
 * <ul>
 *   <li>返回类型：{@code SearchDoc} 而非 {@code Document}</li>
 *   <li>doc 构造：直接 new SearchDoc 而非 new Document(id, content, meta)</li>
 *   <li>RRF 融合逻辑完全一致</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

    @Mock EsVectorSearchService vectorSearchService;
    @Mock EsKeywordSearchService keywordSearchService;
    @Mock RerankService rerankService;

    private HybridSearchService hybridSearchService;

    @BeforeEach
    void setup() {
        hybridSearchService = new HybridSearchService(vectorSearchService, keywordSearchService, rerankService);
    }

    private SearchDoc doc(String content) {
        return new SearchDoc(content, new HashMap<>(Map.of("source", "test")));
    }

    @Test
    @DisplayName("RRF：同时命中两路的文档排名应更高")
    void rrf_bothHitsRankHigher() {
        // 向量路返回 [A, B, C]
        when(vectorSearchService.searchWithOwnership(eq("q"), eq("u1"), anyInt()))
                .thenReturn(List.of(doc("A"), doc("B"), doc("C")));
        // BM25 路返回 [B, D, E] —— B 两路都有
        when(keywordSearchService.search(eq("q"), eq("u1"), anyInt()))
                .thenReturn(List.of(doc("B"), doc("D"), doc("E")));
        when(rerankService.isEnabled()).thenReturn(false);

        List<SearchDoc> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

        assertThat(result).isNotEmpty();
        // B 同时命中两路，应排第一
        assertThat(result.get(0).content()).isEqualTo("B");
    }

    @Test
    @DisplayName("BM25 异常 → 仅返回向量结果（降级）")
    void bm25Failure_fallbackToVector() {
        when(vectorSearchService.searchWithOwnership(eq("q"), eq("u1"), anyInt()))
                .thenReturn(List.of(doc("V1"), doc("V2")));
        when(keywordSearchService.search(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("ES down"));
        when(rerankService.isEnabled()).thenReturn(false);

        // 熔断后降级到纯向量
        List<SearchDoc> result = hybridSearchService.searchWithOwnership("q", "u1", 5);
        assertThat(result).isNotEmpty();
        assertThat(result).extracting(SearchDoc::content).contains("V1", "V2");
    }

    @Test
    @DisplayName("两路全空 → 返回空列表")
    void bothEmpty() {
        when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(keywordSearchService.search(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(rerankService.isEnabled()).thenReturn(false);

        List<SearchDoc> result = hybridSearchService.searchWithOwnership("q", "u1", 5);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Rerank 启用时应截断到 topK")
    void rerankEnabled_truncate() {
        List<SearchDoc> many = List.of(doc("A"), doc("B"), doc("C"), doc("D"), doc("E"));
        when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
                .thenReturn(many);
        when(keywordSearchService.search(anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        when(rerankService.isEnabled()).thenReturn(true);
        when(rerankService.rerank(eq("q"), anyList(), eq(3)))
                .thenReturn(List.of(doc("A"), doc("C"), doc("E")));

        List<SearchDoc> result = hybridSearchService.searchWithOwnership("q", "u1", 3);
        assertThat(result).hasSize(3);
    }
}
