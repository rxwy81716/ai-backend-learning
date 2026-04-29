package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES BM25 关键词检索服务（混合检索的"另一半"）
 *
 * <p>与 {@link EsVectorSearchService} 配合做 Hybrid Search：
 *
 * <ul>
 *   <li>向量检索：擅长语义相近（"如何提速"匹配"性能优化"）
 *   <li>BM25 关键词：擅长精确词命中（专有名词、英文术语、版本号等）
 * </ul>
 *
 * <p>对 Spring AI ElasticsearchVectorStore 的索引结构做 BM25 检索：
 *
 * <pre>
 * {
 *   "content": "...",
 *   "metadata": { "doc_scope": "PUBLIC|PRIVATE", "user_id": "...", "source": "..." },
 *   "embedding": [...]
 * }
 * </pre>
 *
 * <p>注意：默认 ES mapping 用 standard 分词器，对中文召回偏弱。 推荐执行 {@code db/es_index_ik.md} 中的迁移脚本，用 ik_max_word
 * 重建索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsKeywordSearchService {

  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Value("${spring.ai.vectorstore.elasticsearch.index-name:knowledge_vector_store}")
  private String indexName;

  /**
   * 关键词检索（带用户归属过滤）
   *
   * @param query 用户问题
   * @param userId 用户 ID（null = 仅查公共文档）
   * @param topK 召回数
   * @return Spring AI Document 列表（带 BM25 score 写入 metadata.bm25_score）
   */
  public List<Document> searchWithOwnership(String query, String userId, int topK) {
    try {
      String body = buildQueryBody(query, userId, topK);
      Request request = new Request("POST", "/" + indexName + "/_search");
      request.setJsonEntity(body);

      Response response = restClient.performRequest(request);
      JsonNode root = objectMapper.readTree(response.getEntity().getContent());
      JsonNode hits = root.path("hits").path("hits");

      List<Document> results = new ArrayList<>();
      for (JsonNode hit : hits) {
        String id = hit.path("_id").asText();
        double score = hit.path("_score").asDouble();
        JsonNode src = hit.path("_source");

        String content = src.path("content").asText("");
        Map<String, Object> metadata = parseMetadata(src.path("metadata"));
        metadata.put("bm25_score", score);

        results.add(new Document(id, content, metadata));
      }
      log.debug("BM25 检索完成 | query={}, userId={}, hits={}", query, userId, results.size());
      return results;
    } catch (IOException e) {
      log.error("BM25 检索失败 | query={}, err={}", query, e.getMessage(), e);
      return List.of();
    }
  }

  /**
   * 构造 ES Query DSL： - must: match content - filter: doc_scope == PUBLIC OR (doc_scope == PRIVATE
   * AND user_id == userId)
   */
  private String buildQueryBody(String query, String userId, int topK) throws IOException {
    Map<String, Object> matchContent =
        Map.of("match", Map.of("content", Map.of("query", query, "operator", "or")));

    // 用户归属过滤（与向量路径保持一致）
    Map<String, Object> ownershipFilter;
    if (userId != null && !userId.isBlank()) {
      ownershipFilter =
          Map.of(
              "bool",
              Map.of(
                  "should",
                  List.of(
                      Map.of("term", Map.of("metadata.doc_scope.keyword", "PUBLIC")),
                      Map.of(
                          "bool",
                          Map.of(
                              "must",
                              List.of(
                                  Map.of("term", Map.of("metadata.doc_scope.keyword", "PRIVATE")),
                                  Map.of("term", Map.of("metadata.user_id.keyword", userId)))))),
                  "minimum_should_match",
                  1));
    } else {
      ownershipFilter = Map.of("term", Map.of("metadata.doc_scope.keyword", "PUBLIC"));
    }

    Map<String, Object> bool =
        Map.of(
            "must", List.of(matchContent),
            "filter", List.of(ownershipFilter));

    Map<String, Object> body = new HashMap<>();
    body.put("size", topK);
    body.put("query", Map.of("bool", bool));
    // 仅返回需要的字段，减小网络开销
    body.put("_source", List.of("content", "metadata"));

    return objectMapper.writeValueAsString(body);
  }

  private Map<String, Object> parseMetadata(JsonNode metaNode) {
    Map<String, Object> result = new HashMap<>();
    if (metaNode == null || metaNode.isMissingNode() || metaNode.isNull()) {
      return result;
    }
    for (Map.Entry<String, JsonNode> entry : metaNode.properties()) {
      JsonNode v = entry.getValue();
      if (v.isTextual()) result.put(entry.getKey(), v.asText());
      else if (v.isNumber()) result.put(entry.getKey(), v.numberValue());
      else if (v.isBoolean()) result.put(entry.getKey(), v.asBoolean());
      else result.put(entry.getKey(), v.toString());
    }
    return result;
  }
}
