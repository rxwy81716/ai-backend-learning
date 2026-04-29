package com.jianbo.crawler.crawler.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.UserAgentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知乎热榜爬虫（公开 JSON 接口）
 *
 * 接口地址：https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total
 * 返回格式：{"data":[{"target":{"title":"xxx","excerpt":"xxx",...},"detail_text":"xxx 万热度"},...]}
 *
 * 优势：
 *   - 直接返回结构化 JSON，无需解析 HTML
 *   - 数据完整，包含标题、摘要、热度、链接等
 *
 * 注意：
 *   - 知乎可能对频繁请求做限流，建议控制采集频率
 *   - 接口可能变更，需关注返回结构变化
 */
@Slf4j
@Component
public class ZhihuHotCrawler implements CrawlerStrategy {

    private static final String API_URL = "https://www.zhihu.com/api/v3/feed/topstory/hot-lists/total?limit=50";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public ZhihuHotCrawler(@Qualifier("commonRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CrawlSource getSource() {
        return CrawlSource.ZHIHU_HOT;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集知乎热榜 ...");

        try {
            // 1. 请求知乎热榜 API
            String json = restClient.get()
                    .uri(API_URL)
                    .header("User-Agent", UserAgentUtil.randomDesktop())
                    .header("Referer", "https://www.zhihu.com/hot")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            // 2. 解析 JSON 响应
            JsonNode root = MAPPER.readTree(json);
            JsonNode dataArray = root.path("data");

            List<HotItem> items = new ArrayList<>();
            if (dataArray.isArray()) {
                int rank = 1;
                for (JsonNode node : dataArray) {
                    JsonNode target = node.path("target");

                    // 标题
                    String title = target.path("title").asText("").trim();
                    if (title.isBlank()) continue;

                    // 摘要
                    String excerpt = target.path("excerpt").asText("").trim();

                    // 问题 ID → 构造链接
                    long questionId = target.path("id").asLong(0);
                    String url = questionId > 0
                            ? "https://www.zhihu.com/question/" + questionId
                            : "";

                    // 热度文本（如 "1234 万热度"）
                    String detailText = node.path("detail_text").asText("").trim();

                    // 回答数
                    int answerCount = target.path("answer_count").asInt(0);
                    // 关注数
                    int followerCount = target.path("follower_count").asInt(0);

                    Map<String, String> metadata = new LinkedHashMap<>();
                    if (answerCount > 0) metadata.put("回答数", String.valueOf(answerCount));
                    if (followerCount > 0) metadata.put("关注数", String.valueOf(followerCount));

                    items.add(new HotItem(
                            title, excerpt, url,
                            CrawlSource.ZHIHU_HOT,
                            rank++, detailText,
                            metadata, LocalDateTime.now()
                    ));
                }
            }

            long cost = System.currentTimeMillis() - start;
            log.info("知乎热榜采集完成：{} 条, 耗时 {}ms", items.size(), cost);
            return CrawlResult.success(CrawlSource.ZHIHU_HOT, items, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("知乎热榜采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.ZHIHU_HOT, e.getMessage(), cost);
        }
    }
}
