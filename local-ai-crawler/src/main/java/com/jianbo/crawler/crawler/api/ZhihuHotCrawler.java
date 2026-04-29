package com.jianbo.crawler.crawler.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.UserAgentUtil;
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
 * 知乎热榜爬虫
 *
 * 使用知乎 Top Search API（无需登录）：
 *   https://www.zhihu.com/api/v4/search/top_search
 * 该接口返回「大家都在搜」列表，公开可访问。
 *
 * 降级方案：若 top_search 失败，尝试知乎热搜聚合 API。
 */
@Slf4j
@Component
public class ZhihuHotCrawler implements CrawlerStrategy {

    /** 知乎「大家都在搜」接口 — 无需登录 */
    private static final String TOP_SEARCH_URL = "https://www.zhihu.com/api/v4/search/top_search";
    /** 降级：知乎热搜 trending 接口 */
    private static final String TRENDING_URL = "https://www.zhihu.com/api/v4/creators/rank/hot?domain=0&period=hour&limit=50&offset=0";

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
            // 方案一：top_search 接口
            List<HotItem> items = fetchTopSearch();

            // 方案二降级：trending 接口
            if (items.isEmpty()) {
                log.info("top_search 无数据，尝试 trending 接口");
                items = fetchTrending();
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

    /**
     * 方案一：知乎 top_search（大家都在搜）
     * 返回格式：{"top_search":{"data":[{"query":"xxx","display_query":"xxx","score":123},...]},...}
     */
    private List<HotItem> fetchTopSearch() {
        List<HotItem> items = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri(TOP_SEARCH_URL)
                    .header("User-Agent", UserAgentUtil.randomDesktop())
                    .header("Referer", "https://www.zhihu.com/")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(json);
            JsonNode dataArray = root.path("top_search").path("data");
            if (!dataArray.isArray()) {
                dataArray = root.path("data");
            }
            if (!dataArray.isArray()) return items;

            int rank = 1;
            for (JsonNode node : dataArray) {
                String query = node.path("display_query").asText("").trim();
                if (query.isBlank()) {
                    query = node.path("query").asText("").trim();
                }
                if (query.isBlank()) continue;

                String url = "https://www.zhihu.com/search?q=" + query + "&type=content";
                double score = node.path("score").asDouble(0);
                String hotScore = score > 0 ? formatScore(score) : "";

                items.add(new HotItem(
                        query, null, url,
                        CrawlSource.ZHIHU_HOT,
                        rank++, hotScore,
                        Map.of(), LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.warn("知乎 top_search 请求失败：{}", e.getMessage());
        }
        return items;
    }

    /**
     * 方案二（降级）：知乎 creators/rank/hot（热门内容排行）
     */
    private List<HotItem> fetchTrending() {
        List<HotItem> items = new ArrayList<>();
        try {
            String json = restClient.get()
                    .uri(TRENDING_URL)
                    .header("User-Agent", UserAgentUtil.randomDesktop())
                    .header("Referer", "https://www.zhihu.com/")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(json);
            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray()) return items;

            int rank = 1;
            for (JsonNode node : dataArray) {
                JsonNode question = node.path("question");
                String title = question.path("title").asText("").trim();
                if (title.isBlank()) {
                    title = node.path("title").asText("").trim();
                }
                if (title.isBlank()) continue;

                String excerpt = question.path("excerpt").asText("").trim();
                long qid = question.path("id").asLong(0);
                String url = qid > 0 ? "https://www.zhihu.com/question/" + qid : "";

                int answerCount = question.path("answer_count").asInt(0);
                int followerCount = question.path("follower_count").asInt(0);

                Map<String, String> metadata = new LinkedHashMap<>();
                if (answerCount > 0) metadata.put("回答数", String.valueOf(answerCount));
                if (followerCount > 0) metadata.put("关注数", String.valueOf(followerCount));

                items.add(new HotItem(
                        title, excerpt, url,
                        CrawlSource.ZHIHU_HOT,
                        rank++, "",
                        metadata, LocalDateTime.now()
                ));
            }
        } catch (Exception e) {
            log.warn("知乎 trending 请求失败：{}", e.getMessage());
        }
        return items;
    }

    private String formatScore(double score) {
        if (score >= 10000) {
            return String.format("%.1f 万热度", score / 10000);
        }
        return String.valueOf((long) score);
    }
}
