package com.jianbo.localaiknowledge.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES BM25 关键词检索服务（对标 Spring AI 版）。
 *
 * <h2>差异</h2>
 * <p>返回类型从 {@code List<Document>} → {@code List<SearchDoc>}。
 * ES Java Client 查询逻辑与原版完全一致，不依赖任何 AI 框架。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsKeywordSearchService {

    private final ElasticsearchClient esClient;

    @Value("${app.es.index-name:knowledge_vector_store}")
    private String indexName;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<SearchDoc> search(String query, String userId, int topK) {
        try {
            Query matchContent = Query.of(q -> q.matchPhrase(m -> m.field("content").query(query)));
            Query ownership = buildOwnershipQuery(userId);

            SourceFilter sourceFilter = SourceFilter.of(s -> s.includes("content", "metadata"));

            SearchResponse<Map> response = esClient.search(
                    s -> s.index(indexName)
                            .size(topK)
                            .source(src -> src.filter(sourceFilter))
                            .query(q -> q.bool(b -> b.must(matchContent).filter(ownership))),
                    Map.class);

            List<SearchDoc> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map source = hit.source();
                if (source == null) continue;
                String content = String.valueOf(source.getOrDefault("content", ""));
                Map<String, Object> metadata = normalizeMetadata((Map<?, ?>) source.get("metadata"));
                if (hit.score() != null) metadata.put("bm25_score", hit.score());
                results.add(new SearchDoc(content, metadata));
            }
            log.debug("[BM25] query={}, userId={}, hits={}", query, userId, results.size());
            return results;
        } catch (IOException e) {
            log.error("[BM25] 检索失败 | query={}, err={}", query, e.getMessage());
            return List.of();
        }
    }

    private Query buildOwnershipQuery(String userId) {
        if (userId == null || userId.isBlank()) {
            return Query.of(q -> q.term(t -> t.field("metadata.doc_scope").value("PUBLIC")));
        }
        Query publicDocs = Query.of(q -> q.term(t -> t.field("metadata.doc_scope").value("PUBLIC")));
        Query myPrivateDocs = Query.of(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("metadata.doc_scope").value("PRIVATE")))
                .must(m -> m.term(t -> t.field("metadata.user_id").value(userId)))));
        return Query.of(q -> q.bool(b -> b.should(publicDocs).should(myPrivateDocs).minimumShouldMatch("1")));
    }

    private Map<String, Object> normalizeMetadata(Map<?, ?> metaNode) {
        Map<String, Object> result = new HashMap<>();
        if (metaNode == null) return result;
        for (Map.Entry<?, ?> entry : metaNode.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
