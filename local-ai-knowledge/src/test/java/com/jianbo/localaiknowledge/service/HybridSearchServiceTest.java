package com.jianbo.localaiknowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HybridSearchService 单元测试
 *
 * <p>重点验证： 1. RRF 融合分数计算正确（同时命中两路 > 单路命中） 2. 用户归属、topK 透传正确 3. 单路异常时降级（另一路结果仍可用） 4. hybrid
 * 关闭时直接走纯向量 5. 元数据回写（vector_rank / bm25_rank / hybrid_score）
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

  @Mock EsVectorSearchService vectorSearchService;

  @Mock EsKeywordSearchService keywordSearchService;

  @InjectMocks HybridSearchService hybridSearchService;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(hybridSearchService, "hybridEnabled", true);
    ReflectionTestUtils.setField(hybridSearchService, "vectorTopK", 30);
    ReflectionTestUtils.setField(hybridSearchService, "keywordTopK", 30);
    ReflectionTestUtils.setField(hybridSearchService, "rrfK", 60);
    ReflectionTestUtils.setField(hybridSearchService, "parallelTimeoutMs", 3000L);
    ReflectionTestUtils.setField(hybridSearchService, "similarityThreshold", 0.25);
  }

  private Document doc(String id, String content) {
    return new Document(id, content, new HashMap<>());
  }

  @Test
  @DisplayName("RRF：同时命中两路 排名应高于 仅单路命中")
  void rrf_bothHits_rankHigherThanSingleHit() {
    // 向量路径返回 [A, B, C]
    when(vectorSearchService.searchWithOwnership(eq("q"), eq("u1"), eq(30), anyDouble()))
        .thenReturn(List.of(doc("A", "ca"), doc("B", "cb"), doc("C", "cc")));
    // 关键词路径返回 [B, D, E] —— B 在两路都命中
    when(keywordSearchService.searchWithOwnership(eq("q"), eq("u1"), eq(30)))
        .thenReturn(List.of(doc("B", "cb"), doc("D", "cd"), doc("E", "ce")));

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

    assertThat(result).hasSize(5);
    // B 同时命中两路（rank=2 + rank=1），应排第一
    assertThat(result.get(0).getId()).isEqualTo("B");

    // hybrid_score 单调递减
    double prev = Double.MAX_VALUE;
    for (Document d : result) {
      double s = ((Number) d.getMetadata().get("hybrid_score")).doubleValue();
      assertThat(s).isLessThanOrEqualTo(prev);
      prev = s;
    }
  }

  @Test
  @DisplayName("RRF 分数公式：1/(k+rank) 累加")
  void rrf_score_formulaCorrect() {
    when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(doc("X", "x"))); // X: vector rank=1
    when(keywordSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
        .thenReturn(List.of(doc("Y", "y"), doc("X", "x"))); // X: bm25 rank=2

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

    Document x = result.stream().filter(d -> "X".equals(d.getId())).findFirst().orElseThrow();
    // X: 1/(60+1) + 1/(60+2)
    double expected = 1.0 / 61 + 1.0 / 62;
    double actual = ((Number) x.getMetadata().get("hybrid_score")).doubleValue();
    assertThat(actual).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));

    assertThat(x.getMetadata()).containsEntry("vector_rank", 1);
    assertThat(x.getMetadata()).containsEntry("bm25_rank", 2);
  }

  @Test
  @DisplayName("topK 截断：返回数量不超过 topK")
  void topK_truncate() {
    when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(doc("A", "a"), doc("B", "b"), doc("C", "c"), doc("D", "d")));
    when(keywordSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
        .thenReturn(List.of(doc("E", "e"), doc("F", "f")));

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 3);
    assertThat(result).hasSize(3);
  }

  @Test
  @DisplayName("BM25 路径异常：仍能返回向量结果（降级）")
  void bm25_fail_fallbackToVector() {
    when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(doc("V1", "v1"), doc("V2", "v2")));
    when(keywordSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
        .thenThrow(new RuntimeException("ES down"));

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(Document::getId).containsExactly("V1", "V2");
  }

  @Test
  @DisplayName("两路全空：返回空列表，不抛异常")
  void bothEmpty_returnEmpty() {
    when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of());
    when(keywordSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
        .thenReturn(List.of());

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("hybrid 关闭：跳过 BM25，仅调用向量检索")
  void hybridDisabled_useVectorOnly() {
    ReflectionTestUtils.setField(hybridSearchService, "hybridEnabled", false);

    when(vectorSearchService.searchWithOwnership(eq("q"), eq("u1"), eq(5), anyDouble()))
        .thenReturn(List.of(doc("A", "a")));

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

    assertThat(result).hasSize(1);
    verify(keywordSearchService, never()).searchWithOwnership(anyString(), anyString(), anyInt());
  }

  @Test
  @DisplayName("元数据保留：原始 metadata 字段不被覆盖")
  void metadata_preserved() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("source", "demo.pdf");
    meta.put("doc_scope", "PUBLIC");
    Document origin = new Document("A", "content", meta);

    when(vectorSearchService.searchWithOwnership(anyString(), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(origin));
    when(keywordSearchService.searchWithOwnership(anyString(), anyString(), anyInt()))
        .thenReturn(List.of());

    List<Document> result = hybridSearchService.searchWithOwnership("q", "u1", 5);

    assertThat(result).hasSize(1);
    Map<String, Object> resultMeta = result.get(0).getMetadata();
    assertThat(resultMeta).containsEntry("source", "demo.pdf");
    assertThat(resultMeta).containsEntry("doc_scope", "PUBLIC");
    assertThat(resultMeta).containsKey("hybrid_score");
    assertThat(resultMeta).containsEntry("vector_rank", 1);
  }
}
