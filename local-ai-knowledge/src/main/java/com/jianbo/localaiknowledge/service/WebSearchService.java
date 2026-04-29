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
 * 网络搜索服务（当知识库无结果时降级调用）
 *
 * <p>支持 Tavily API（专为 AI 优化的搜索引擎） 也可替换为 SerpAPI / Bing Search API / Google Custom Search
 *
 * <p>配置： app.web-search.provider: tavily / serpapi（默认 tavily） app.web-search.api-key: 你的 API Key
 * app.web-search.max-results: 3
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

  /**
   * 同步 RestClient：底层走 JDK HttpClient，避开 Reactor Netty。
   *
   * <p>原因：本服务可能被 Spring AI Tool Calling 在流式 chat 的 Reactor 线程里调用，原先的
   * {@code WebClient + .block()} 会触发 BlockHound 检测异常。换成 JDK HttpClient 后是普通阻塞 IO，无线程亲和限制。
   */
  private static final RestClient REST_CLIENT =
      RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()).build();

  /**
   * 执行网络搜索
   *
   * @param query 搜索关键词
   * @return 搜索结果列表（每条包含 title + content + url）
   */
  public List<Map<String, String>> search(String query) {
    if (!enabled || apiKey == null || apiKey.isBlank()) {
      log.debug("网络搜索未启用或未配置 API Key");
      return List.of();
    }

    try {
      log.info("发起网络搜索 | query={}", query);
      long start = System.currentTimeMillis();

      Map<String, Object> requestBody =
          Map.of(
              "api_key", apiKey,
              "query", query,
              "max_results", maxResults,
              "include_answer", true,
              "search_depth", "basic");

      @SuppressWarnings("unchecked")
      Map<String, Object> response =
          REST_CLIENT
              .post()
              .uri(TAVILY_URL)
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(Map.class);

      long cost = System.currentTimeMillis() - start;
      log.info("网络搜索完成 | 耗时 {}ms", cost);

      if (response == null) {
        return List.of();
      }

      // 提取搜索结果
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
      if (results == null || results.isEmpty()) {
        return List.of();
      }

      return results.stream()
          .limit(maxResults)
          .map(
              r ->
                  Map.of(
                      "title", String.valueOf(r.getOrDefault("title", "")),
                      "content", String.valueOf(r.getOrDefault("content", "")),
                      "url", String.valueOf(r.getOrDefault("url", ""))))
          .toList();

    } catch (Exception e) {
      log.error("网络搜索失败: {}", e.getMessage());
      return List.of();
    }
  }

  /** 将搜索结果格式化为上下文文本（供 LLM 使用） */
  public String formatAsContext(List<Map<String, String>> results) {
    if (results.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < results.size(); i++) {
      Map<String, String> r = results.get(i);
      sb.append("【").append(i + 1).append("】[来源: ").append(r.get("url")).append("]\n");
      sb.append(r.get("title")).append("\n");
      sb.append(r.get("content")).append("\n\n");
    }
    return sb.toString();
  }

  public boolean isEnabled() {
    return enabled && apiKey != null && !apiKey.isBlank();
  }
}
