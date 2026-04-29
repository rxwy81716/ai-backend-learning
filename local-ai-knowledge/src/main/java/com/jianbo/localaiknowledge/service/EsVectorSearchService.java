package com.jianbo.localaiknowledge.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * ES 向量检索服务
 *
 * <p>和 VectorSearchService（PG版）代码几乎一样 唯一区别：注入的是 @Qualifier("esVectorStore") 的
 * ElasticsearchVectorStore --> 面向接口编程，底层存储对上层透明
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorSearchService {

  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_THRESHOLD = 0.0;

  private final VectorStore vectorStore;

  // ==================== 便捷重载 ====================

  public List<Document> search(String query) {
    return search(query, null, null, null, null);
  }

  public List<Document> search(String query, int topK) {
    return search(query, null, null, topK, null);
  }

  public List<Document> search(String query, int topK, double similarityThreshold) {
    return search(query, null, null, topK, similarityThreshold);
  }

  public List<Document> search(String query, String source, int topK) {
    return search(query, List.of(source), null, topK, null);
  }

  public List<Document> search(
      String query, List<String> sources, Integer minChunks, Integer topK) {
    return search(query, sources, minChunks, topK, null);
  }

  // ==================== 核心方法 ====================

  /**
   * 统一检索入口（参数为 null 自动跳过）
   *
   * @param query 用户问题（必填）
   * @param sources 文档来源列表（null = 不过滤）
   * @param minChunks 最少片段数（null = 不过滤）
   * @param topK 召回数量（null = 默认5）
   * @param similarityThreshold 相似度阈值（null = 不过滤）
   */
  public List<Document> search(
      String query,
      List<String> sources,
      Integer minChunks,
      Integer topK,
      Double similarityThreshold) {
    int k = (topK != null) ? topK : DEFAULT_TOP_K;
    double threshold = (similarityThreshold != null) ? similarityThreshold : DEFAULT_THRESHOLD;

    log.debug(
        "ES语义检索 | query={}, sources={}, minChunks={}, topK={}, threshold={}",
        query,
        sources,
        minChunks,
        k,
        threshold);

    Filter.Expression filter = buildFilter(sources, minChunks);

    SearchRequest.Builder builder =
        SearchRequest.builder().query(query).topK(k).similarityThreshold(threshold);

    if (filter != null) {
      builder.filterExpression(filter);
    }

    List<Document> results = vectorStore.similaritySearch(builder.build());
    log.debug("ES检索完成, 召回 {} 条", results.size());
    return results;
  }

  // ==================== 用户隔离检索 ====================

  /**
   * 用户隔离检索：只返回该用户的私有文档 + 公共文档（爬虫数据）
   *
   * <p>过滤逻辑：(doc_scope == PUBLIC) OR (doc_scope == PRIVATE AND user_id == userId)
   */
  public List<Document> searchWithOwnership(
      String query, String userId, int topK, double similarityThreshold) {
    log.debug("用户隔离检索 | query={}, userId={}, topK={}", query, userId, topK);

    var b = new FilterExpressionBuilder();

    Filter.Expression filter;
    if (userId != null && !userId.isBlank()) {
      // 公共文档 OR 该用户的私有文档
      filter =
          b.or(
                  b.eq("doc_scope", "PUBLIC"),
                  b.and(b.eq("doc_scope", "PRIVATE"), b.eq("user_id", userId)))
              .build();
    } else {
      // 未登录：只看公共文档
      filter = b.eq("doc_scope", "PUBLIC").build();
    }

    SearchRequest request =
        SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .filterExpression(filter)
            .build();

    List<Document> results = vectorStore.similaritySearch(request);
    log.debug("用户隔离检索完成 | userId={}, 召回 {} 条", userId, results.size());
    return results;
  }

  // ==================== 动态 Filter ====================

  private Filter.Expression buildFilter(List<String> sources, Integer minChunks) {
    var b = new FilterExpressionBuilder();
    List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();

    if (sources != null && !sources.isEmpty()) {
      conditions.add(b.in("source", sources.toArray()));
    }
    if (minChunks != null) {
      conditions.add(b.gte("total_chunks", minChunks));
    }
    // 未来加新条件：
    // if (xxx != null) { conditions.add(b.eq("xxx", xxx)); }

    if (conditions.isEmpty()) {
      return null;
    }
    FilterExpressionBuilder.Op result = conditions.get(0);
    for (int i = 1; i < conditions.size(); i++) {
      result = b.and(result, conditions.get(i));
    }
    return result.build();
  }
}
