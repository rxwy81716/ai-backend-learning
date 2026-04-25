package com.jianbo.springai.service.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.springai.service.save.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 手动 KNN 检索（自定义场景）
 *
 * <p>适用场景： - 需要 ES 原生 query + knn 混合检索 - 需要分页、聚合等高级特性 - 需要自定义打分公式
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsKnnSearchService {
  private final RestClient restClient;
  private final EmbeddingService embeddingService;
  private final ObjectMapper objectMapper;
  private static final String INDEX_NAME = "my_documents_es";
  private static final int DEFAULT_TOP_K = 5;
  private static final int DEFAULT_NUM_CANDIDATES = 100;


  // ==================== 便捷重载 ====================

  public List<Map<String, Object>> knnSearch(String query) throws IOException {
    return knnSearch(query, null, null);
  }

  public List<Map<String, Object>> knnSearch(String query, int topK) throws IOException {
    return knnSearch(query, topK, null);
  }
  // ==================== 核心 KNN 检索 ====================

  /**
   * 纯 KNN 向量检索（支持可选 source 过滤）
   *
   * @param queryText 用户问题（必填）
   * @param topK      召回数量（null = 默认5）
   * @param source    文档来源过滤（null = 不过滤）
   */
  public List<Map<String, Object>> knnSearch(String queryText,
                                             Integer topK,
                                             String source) throws IOException {
    int k = (topK != null) ? topK : DEFAULT_TOP_K;
    float[] queryVector = embeddingService.embed(queryText);
    // 动态拼 JSON
    String filterClause = "";
    if (source != null && !source.isEmpty()) {
      filterClause = """
                , "filter": { "term": { "source": "%s" } }
                """.formatted(source);
    }
    String knnBody = """
            {
              "knn": {
                "field": "embedding",
                "query_vector": %s,
                "k": %d,
                "num_candidates": %d
                %s
              },
              "_source": ["content", "source", "chunk_index", "doc_title"]
            }
            """.formatted(vectorToJson(queryVector), k, DEFAULT_NUM_CANDIDATES, filterClause);

    Request request = new Request("POST", "/" + INDEX_NAME + "/_search");
    request.setJsonEntity(knnBody);
    Response response = restClient.performRequest(request);

    List<Map<String, Object>> results = parseHits(response);
    log.info("ES KNN 检索完成, 召回 {} 条", results.size());
    return results;
  }

  // ==================== 混合检索 ====================

  /**
   * 混合检索：KNN 向量 + 全文关键词
   *
   * <p>原理：
   *   knn（顶层） → 语义相似度打分
   *   query.match  → 关键词匹配打分
   *   ES 自动合并两个分数
   *
   * @param query  用户问题（必填，同时用于向量化和关键词匹配）
   * @param topK   召回数量（null = 默认5）
   * @param source 文档来源过滤（null = 不过滤）
   */
  public List<Map<String, Object>> hybridSearch(String query,
                                                Integer topK,
                                                String source) throws IOException {
    int k = (topK != null) ? topK : DEFAULT_TOP_K;
    float[] queryVector = embeddingService.embed(query);

    // knn filter
    String knnFilter = "";
    if (source != null && !source.isEmpty()) {
      knnFilter = """
                , "filter": { "term": { "source": "%s" } }
                """.formatted(source);
    }
    // query filter
    String queryFilter = "";
    if (source != null && !source.isEmpty()) {
      queryFilter = """
                , "filter": [ { "term": { "source": "%s" } } ]
                """.formatted(source);
    }

    // ES 8.x 混合检索：knn 在顶层，query 也在顶层，ES 自动合并分数
    String hybridBody = """
            {
              "knn": {
                "field": "embedding",
                "query_vector": %s,
                "k": %d,
                "num_candidates": %d
                %s
              },
              "query": {
                "bool": {
                  "must": [
                    { "match": { "content": "%s" } }
                  ]
                  %s
                }
              },
              "_source": ["content", "source", "chunk_index", "doc_title"],
              "size": %d
            }
            """.formatted(
            vectorToJson(queryVector), k, DEFAULT_NUM_CANDIDATES, knnFilter,
            escapeJson(query), queryFilter, k);

    Request request = new Request("POST", "/" + INDEX_NAME + "/_search");
    request.setJsonEntity(hybridBody);
    Response response = restClient.performRequest(request);

    List<Map<String, Object>> results = parseHits(response);
    log.info("ES 混合检索完成, 召回 {} 条", results.size());
    return results;
  }

  // ==================== 工具方法 ====================

  /**
   * 解析 ES 响应中的 hits
   * ES 返回结构：{ "hits": { "hits": [ { "_score": 0.9, "_source": {...} } ] } }
   */
  private List<Map<String, Object>> parseHits(Response response) throws IOException {
    JsonNode root = objectMapper.readTree(response.getEntity().getContent());
    JsonNode hits = root.path("hits").path("hits");

    List<Map<String, Object>> results = new ArrayList<>();
    for (JsonNode hit : hits) {
      Map<String, Object> item = new HashMap<>();
      item.put("score", hit.path("_score").asDouble());
      item.put("id", hit.path("_id").asText());
      // 展开 _source 中的字段
      JsonNode source = hit.path("_source");
      source.fields().forEachRemaining(f -> item.put(f.getKey(), f.getValue().asText()));
      results.add(item);
    }
    return results;
  }

  /** float[] 转 JSON 数组字符串 */
  private String vectorToJson(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  /** 转义 JSON 字符串中的特殊字符 */
  private String escapeJson(String text) {
    return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n");
  }
}