package com.jianbo.springai.service.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索服务（语义检索核心）
 *
 * <p>所有重载方法最终都委托给核心方法 search(query, sources, minChunks, topK, similarityThreshold)
 * 参数为 null 则自动跳过该条件
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VectorSearchService {

  private static final int DEFAULT_TOP_K = 5;
  private static final double DEFAULT_THRESHOLD = 0.0;

  private final VectorStore vectorStore;
  // ==================== 便捷重载（全部委托核心方法） ====================

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

  public List<Document> search(String query, List<String> sources, Integer minChunks, Integer topK) {
    return search(query, sources, minChunks, topK, null);
  }

  // ==================== 核心方法（参数为 null 自动跳过） ====================

  /**
   * 统一检索入口
   *
   * @param query               用户问题（必填）
   * @param sources             文档来源列表（null = 不过滤来源）
   * @param minChunks           最少片段数（null = 不过滤片段数）
   * @param topK                召回数量（null = 默认5）
   * @param similarityThreshold 相似度阈值（null = 不过滤，0.0~1.0）
   * @return 满足条件的文档片段列表
   */
  public List<Document> search(String query,
                               List<String> sources,
                               Integer minChunks,
                               Integer topK,
                               Double similarityThreshold) {
    int k = (topK != null) ? topK : DEFAULT_TOP_K;
    double threshold = (similarityThreshold != null) ? similarityThreshold : DEFAULT_THRESHOLD;

    log.debug("语义检索 | query={}, sources={}, minChunks={}, topK={}, threshold={}",
            query, sources, minChunks, k, threshold);

    // 动态构建 filter：有值才拼，null 跳过
    Filter.Expression filter = buildFilter(sources, minChunks, null, null);

    // 构建 SearchRequest
    SearchRequest.Builder builder = SearchRequest.builder()
            .query(query)
            .topK(k)
            .similarityThreshold(threshold);

    if (filter != null) {
      builder.filterExpression(filter);
    }

    List<Document> results = vectorStore.similaritySearch(builder.build());
    log.debug("检索完成, 召回 {} 条", results.size());
    return results;
  }

  // ==================== 动态拼 Filter ====================

  /**
   * 动态拼 Filter（List 收集法，条件再多也不怕）
   * 非 null 的条件自动加入，最后用 AND 串联
   */
  private Filter.Expression buildFilter(List<String> sources,
                                        Integer minChunks,
                                        String docTitle,
                                        Integer minChunkIndex) {
    // ... 未来加新参数，这里加一个 if 就行
    var b = new FilterExpressionBuilder();
    List<FilterExpressionBuilder.Op> conditions = new ArrayList<>();

    if (sources != null && !sources.isEmpty()) {
      conditions.add(b.in("source", sources.toArray()));
    }
    if (minChunks != null) {
      conditions.add(b.gte("total_chunks", minChunks));
    }
    if (docTitle != null) {
      conditions.add(b.eq("doc_title", docTitle));
    }
    if (minChunkIndex != null) {
      conditions.add(b.gte("chunk_index", minChunkIndex));
    }
    // 未来加新条件：
    // if (xxx != null) { conditions.add(b.eq("xxx", xxx)); }

    // 空 → 不过滤
    if (conditions.isEmpty()) {
      return null;
    }
    // 1个 → 单条件；多个 → 逐个 AND 合并
    FilterExpressionBuilder.Op result = conditions.get(0);
    for (int i = 1; i < conditions.size(); i++) {
      result = b.and(result, conditions.get(i));
    }
    return result.build();
  }
}