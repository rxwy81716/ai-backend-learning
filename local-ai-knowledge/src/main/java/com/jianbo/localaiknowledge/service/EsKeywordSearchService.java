package com.jianbo.localaiknowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>查询使用 ES Java API Client 的类型安全 DSL（编译期校验字段名 / 操作符），
 * 而非手拼 JSON；底层共享 {@link co.elastic.clients.transport.rest5_client.low_level.Rest5Client}
 * 连接池，无额外开销。
 *
 * <p>对应索引结构（与 Spring AI {@code ElasticsearchVectorStore} 一致）：
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

  private final ElasticsearchClient esClient;

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
  @SuppressWarnings({"rawtypes", "unchecked"})
  public List<Document> searchWithOwnership(String query, String userId, int topK) {
    try {
      // 使用 match_phrase 保持短语完整性，避免"蒜薹噩梦"被分词器拆分成"蒜薹"和"噩梦"
      Query matchContent =
          Query.of(q -> q.matchPhrase(m -> m.field("content").query(query)));
      Query ownership = buildOwnershipQuery(userId);

      // _source 只取 content + metadata，减小网络开销
      SourceFilter sourceFilter =
          SourceFilter.of(s -> s.includes("content", "metadata"));

      SearchResponse<Map> response =
          esClient.search(
              s ->
                  s.index(indexName)
                      .size(topK)
                      .source(src -> src.filter(sourceFilter))
                      .query(
                          q ->
                              q.bool(
                                  b -> b.must(matchContent).filter(ownership))),
              Map.class);

      List<Document> results = new ArrayList<>();
      for (Hit<Map> hit : response.hits().hits()) {
        Map source = hit.source();
        if (source == null) continue;
        String content = String.valueOf(source.getOrDefault("content", ""));
        Map<String, Object> metadata = normalizeMetadata((Map<?, ?>) source.get("metadata"));
        if (hit.score() != null) metadata.put("bm25_score", hit.score());
        results.add(new Document(hit.id(), content, metadata));
      }
      log.debug("BM25 检索完成 | query={}, userId={}, hits={}", query, userId, results.size());
      return results;
    } catch (IOException e) {
      log.error("BM25 检索失败 | query={}, err={}", query, e.getMessage(), e);
      return List.of();
    }
  }

  /** 用户归属过滤：PUBLIC OR (PRIVATE AND user_id == userId)。 */
  private Query buildOwnershipQuery(String userId) {
    if (userId == null || userId.isBlank()) {
      return Query.of(q -> q.term(t -> t.field("metadata.doc_scope").value("PUBLIC")));
    }
    Query publicDocs =
        Query.of(q -> q.term(t -> t.field("metadata.doc_scope").value("PUBLIC")));
    Query myPrivateDocs =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.must(
                                m ->
                                    m.term(
                                        t ->
                                            t.field("metadata.doc_scope").value("PRIVATE")))
                            .must(
                                m ->
                                    m.term(
                                        t -> t.field("metadata.user_id").value(userId)))));
    return Query.of(q -> q.bool(b -> b.should(publicDocs).should(myPrivateDocs).minimumShouldMatch("1")));
  }

  /** 把 _source.metadata 节点扁平拷成 Spring AI Document.metadata 接受的 Map<String,Object>。 */
  private Map<String, Object> normalizeMetadata(Map<?, ?> metaNode) {
    Map<String, Object> result = new HashMap<>();
    if (metaNode == null) return result;
    for (Map.Entry<?, ?> entry : metaNode.entrySet()) {
      result.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return result;
  }
}
