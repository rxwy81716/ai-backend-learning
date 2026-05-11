package com.jianbo.localaiknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.service.EsVectorSearchService.SearchDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 交叉编码器重排序（与 Spring AI 版完全一致，不依赖 AI 框架）。
 */
@Service
@Slf4j
public class RerankService {

    @Value("${app.rerank.enabled:false}")
    private boolean enabled;

    @Value("${app.rerank.api-key:}")
    private String apiKey;

    @Value("${app.rerank.model:BAAI/bge-reranker-v2-m3}")
    private String model;

    @Value("${app.rerank.base-url:https://api.siliconflow.cn/v1}")
    private String baseUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public boolean isEnabled() { return enabled && apiKey != null && !apiKey.isBlank(); }

    public List<SearchDoc> rerank(String query, List<SearchDoc> docs, int topN) {
        if (!isEnabled() || docs.isEmpty()) return docs;

        try {
            List<String> texts = docs.stream().map(SearchDoc::content).toList();
            Map<String, Object> body = Map.of(
                    "model", model,
                    "query", query,
                    "documents", texts,
                    "top_n", topN,
                    "return_documents", false
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode results = root.get("results");

            List<SearchDoc> reranked = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    int idx = r.get("index").asInt();
                    double score = r.get("relevance_score").asDouble();
                    if (score > 0.3 && idx < docs.size()) {
                        reranked.add(docs.get(idx));
                    }
                }
            }
            return reranked.isEmpty() ? docs.subList(0, Math.min(topN, docs.size())) : reranked;
        } catch (Exception e) {
            log.warn("[Rerank] 失败，返回原始结果: {}", e.getMessage());
            return docs.subList(0, Math.min(topN, docs.size()));
        }
    }
}
