package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-Encoder Rerank 服务（检索精度天花板）。
 *
 * <p>痛点：向量检索 + BM25 + RRF 融合后的 top-K 仍可能包含"语义相近但实际无关"的噪声文档，
 * 导致 LLM 被无关上下文干扰而产生幻觉。
 *
 * <p>方案：调用 Cross-Encoder Reranker（BAAI/bge-reranker-v2-m3）对候选文档做细粒度打分，
 * 只保留真正相关的 top-N，显著提升 Precision@K。
 *
 * <p>架构位置：{@code HybridSearchService} RRF 融合后 → Rerank → 返回最终 top-K
 *
 * <pre>
 *   vector(30) + BM25(30) → RRF(rerank-top-n) → Rerank(final top-K)
 * </pre>
 *
 * <p>调用 SiliconFlow Rerank API（OpenAI 兼容），延迟 ~200ms，对 20 篇候选文档。
 */
@Slf4j
@Service
public class RerankService {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Value("${app.rag.rerank.enabled:false}")
  private boolean enabled;

  @Value("${app.rag.rerank.model:BAAI/bge-reranker-v2-m3}")
  private String model;

  @Value("${app.rag.rerank.top-n:5}")
  private int defaultTopN;

  @Value("${app.rag.rerank.score-threshold:0.1}")
  private double scoreThreshold;

  @Value("${app.rag.rerank.api-url:https://api.siliconflow.cn/v1/rerank}")
  private String apiUrl;

  @Value("${app.embedding.siliconflow.api-key:${SILICONFLOW_API_KEY:}}")
  private String apiKey;

  public RerankService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory())
        .build();
  }

  public boolean isEnabled() {
    return enabled;
  }

  /**
   * 对候选文档进行 Cross-Encoder 重排序。
   *
   * @param query 用户查询
   * @param candidates RRF 融合后的候选文档
   * @param topN 最终保留条数（0 = 使用默认配置）
   * @return 按 relevance_score 倒序的 top-N 文档（metadata 中追加 rerank_score）
   */
  public List<Document> rerank(String query, List<Document> candidates, int topN) {
    if (!enabled || candidates == null || candidates.isEmpty()) {
      return candidates;
    }
    if (topN <= 0) topN = defaultTopN;

    try {
      long t0 = System.currentTimeMillis();

      // 构建请求体
      List<String> docTexts = new ArrayList<>(candidates.size());
      for (Document doc : candidates) {
        docTexts.add(doc.getText());
      }

      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", model);
      requestBody.put("query", query);
      requestBody.put("documents", docTexts);
      requestBody.put("top_n", Math.min(topN, candidates.size()));
      requestBody.put("return_documents", false);

      String responseStr = restClient.post()
          .uri(apiUrl)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
          .contentType(MediaType.APPLICATION_JSON)
          .body(objectMapper.writeValueAsString(requestBody))
          .retrieve()
          .body(String.class);

      JsonNode root = objectMapper.readTree(responseStr);
      JsonNode results = root.get("results");
      if (results == null || !results.isArray()) {
        log.warn("Rerank API 返回格式异常，跳过重排 | response={}", responseStr);
        return candidates.subList(0, Math.min(topN, candidates.size()));
      }

      List<Document> reranked = new ArrayList<>(results.size());
      for (JsonNode item : results) {
        int index = item.get("index").asInt();
        double score = item.get("relevance_score").asDouble();

        if (score < scoreThreshold) {
          continue;
        }

        if (index >= 0 && index < candidates.size()) {
          Document origin = candidates.get(index);
          Map<String, Object> meta = new HashMap<>(origin.getMetadata());
          meta.put("rerank_score", score);
          reranked.add(new Document(origin.getId(), origin.getText(), meta));
        }
      }

      long cost = System.currentTimeMillis() - t0;
      log.info("🎯 Rerank | model={}, candidates={}, survived={}, cost={}ms",
          model, candidates.size(), reranked.size(), cost);

      return reranked.isEmpty()
          ? candidates.subList(0, Math.min(topN, candidates.size()))
          : reranked;

    } catch (Exception e) {
      log.warn("Rerank 调用失败，降级返回原始排序 | err={}", e.getMessage());
      return candidates.subList(0, Math.min(topN, candidates.size()));
    }
  }
}
