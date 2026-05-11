package com.jianbo.localaiknowledge.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量检索服务（对标 Spring AI 版 EsVectorSearchService）。
 *
 * <h2>核心 API 对比</h2>
 * <pre>
 * Spring AI:
 *   SearchRequest req = SearchRequest.builder()
 *       .query(text)
 *       .topK(5)
 *       .similarityThreshold(0.5)
 *       .filterExpression("user_id == '123' || doc_scope == 'PUBLIC'")
 *       .build();
 *   List&lt;Document&gt; results = vectorStore.similaritySearch(req);
 *
 * LangChain4j:
 *   Embedding queryEmb = embeddingModel.embed(text).content();
 *   EmbeddingSearchRequest req = EmbeddingSearchRequest.builder()
 *       .queryEmbedding(queryEmb)
 *       .maxResults(5)
 *       .minScore(0.5)
 *       .filter(new Or(
 *           new IsEqualTo("user_id", "123"),
 *           new IsEqualTo("doc_scope", "PUBLIC")
 *       ))
 *       .build();
 *   EmbeddingSearchResult&lt;TextSegment&gt; result = embeddingStore.search(req);
 * </pre>
 *
 * <h2>关键差异</h2>
 * <ul>
 *   <li>Spring AI VectorStore 内部自动调 EmbeddingModel，LangChain4j 需要手动调</li>
 *   <li>过滤语法：Spring AI 用字符串表达式，LangChain4j 用 Filter 对象树</li>
 *   <li>返回类型：Spring AI 返回 Document，LangChain4j 返回 EmbeddingMatch&lt;TextSegment&gt;</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EsVectorSearchService {

    private final ElasticsearchEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;

    /**
     * 带用户隔离的向量搜索（核心方法）。
     */
    public List<SearchDoc> searchWithOwnership(String query, String userId, int topK) {
        Embedding queryEmb = embeddingModel.embed(query).content();

        // 构建过滤器：doc_scope='PUBLIC' OR user_id=userId
        Filter filter = buildOwnershipFilter(userId);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmb)
                .maxResults(topK)
                .minScore(0.5)
                .filter(filter)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        List<SearchDoc> docs = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            Map<String, Object> metadata = new HashMap<>();
            if (segment.metadata() != null) {
                metadata.putAll(segment.metadata().toMap());
            }
            metadata.put("score", match.score());
            docs.add(new SearchDoc(segment.text(), metadata));
        }
        return docs;
    }

    /** 基础搜索（无用户隔离） */
    public List<SearchDoc> search(String query, int topK) {
        return searchWithOwnership(query, null, topK);
    }

    private Filter buildOwnershipFilter(String userId) {
        Filter publicFilter = new IsEqualTo("doc_scope", "PUBLIC");
        if (userId == null || userId.isBlank()) {
            return publicFilter;
        }
        Filter userFilter = new IsEqualTo("user_id", userId);
        return new Or(publicFilter, userFilter);
    }

    /**
     * 统一搜索结果类型（对标 Spring AI 的 Document）。
     * 封装内容 + 元数据，让上层不感知 LangChain4j 的 TextSegment。
     */
    public record SearchDoc(String content, Map<String, Object> metadata) {
        public String getContent() { return content; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
