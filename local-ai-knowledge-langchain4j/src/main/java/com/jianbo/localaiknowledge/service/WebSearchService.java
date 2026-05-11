package com.jianbo.localaiknowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 网络搜索服务（与 Spring AI 版完全一致，无 AI 框架依赖）。
 * 使用 Tavily API（专为 AI 优化的搜索引擎）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebSearchService {

    @Value("${app.web-search.api-key:}")
    private String apiKey;

    @Value("${app.web-search.max-results:3}")
    private int maxResults;

    @Value("${app.web-search.enabled:false}")
    private boolean enabled;

    private static final String TAVILY_URL = "https://api.tavily.com/search";

    private static final RestClient REST_CLIENT =
            RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).build();

    public boolean isEnabled() { return enabled && apiKey != null && !apiKey.isBlank(); }

    @SuppressWarnings("unchecked")
    public List<Map<String, String>> search(String query) {
        if (!isEnabled()) {
            log.debug("网络搜索未启用或未配置 API Key");
            return List.of();
        }

        try {
            log.info("发起网络搜索 | query={}", query);
            long start = System.currentTimeMillis();

            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", maxResults,
                    "include_answer", true,
                    "search_depth", "basic");

            Map<String, Object> response = REST_CLIENT.post()
                    .uri(TAVILY_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("网络搜索完成 | 耗时 {}ms", System.currentTimeMillis() - start);

            if (response == null) return List.of();

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results == null || results.isEmpty()) return List.of();

            return results.stream()
                    .limit(maxResults)
                    .map(r -> Map.of(
                            "title", String.valueOf(r.getOrDefault("title", "")),
                            "content", String.valueOf(r.getOrDefault("content", "")),
                            "url", String.valueOf(r.getOrDefault("url", ""))))
                    .toList();
        } catch (Exception e) {
            log.error("网络搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    public String formatAsContext(List<Map<String, String>> results) {
        if (results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Map<String, String> r = results.get(i);
            sb.append("【").append(i + 1).append("】[来源: ").append(r.get("url")).append("]\n");
            sb.append(r.get("title")).append("\n");
            sb.append(r.get("content")).append("\n\n");
        }
        return sb.toString();
    }
}
