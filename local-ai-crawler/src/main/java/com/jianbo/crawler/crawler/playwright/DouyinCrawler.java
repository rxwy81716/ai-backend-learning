package com.jianbo.crawler.crawler.playwright;

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
 * 抖音热榜爬虫（Web API 版）
 *
 * 使用抖音内部 Web API 直接获取热搜列表，无需 Playwright。
 * 接口：https://www.douyin.com/aweme/v1/web/hot/search/list/
 *
 * 注意事项：
 *   - 抖音反爬较严，需带正确的 Referer 和 User-Agent
 *   - 建议采集频率 ≤ 每4小时一次
 */
@Slf4j
@Component
public class DouyinCrawler implements CrawlerStrategy {

    private static final String HOT_SEARCH_URL = "https://www.douyin.com/aweme/v1/web/hot/search/list/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public DouyinCrawler(@Qualifier("commonRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CrawlSource getSource() {
        return CrawlSource.DOUYIN;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集抖音热搜 ...");

        try {
            String json = restClient.get()
                    .uri(HOT_SEARCH_URL + "?device_platform=webapp&aid=6383&channel=channel_pc_web&detail_list=1")
                    .header("User-Agent", UserAgentUtil.randomDesktop())
                    .header("Referer", "https://www.douyin.com/")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Cookie", "")
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(json);
            JsonNode dataNode = root.path("data");
            JsonNode wordList = dataNode.path("word_list");
            if (!wordList.isArray() || wordList.isEmpty()) {
                // 降级尝试 trending_list
                wordList = dataNode.path("trending_list");
            }

            List<HotItem> items = new ArrayList<>();
            if (wordList.isArray()) {
                int rank = 1;
                for (JsonNode node : wordList) {
                    String title = node.path("word").asText("").trim();
                    if (title.isBlank()) {
                        title = node.path("sentence_tag").asText("").trim();
                    }
                    if (title.isBlank()) continue;

                    long hotValue = node.path("hot_value").asLong(0);
                    String hotScore = hotValue > 0 ? formatHotValue(hotValue) : "";

                    String label = node.path("label").asText("").trim();
                    if (label.isBlank()) {
                        int labelType = node.path("label_type").asInt(0);
                        label = switch (labelType) {
                            case 1 -> "热";
                            case 2 -> "新";
                            case 3 -> "飙升";
                            default -> "";
                        };
                    }

                    // 构造搜索链接
                    String url = "https://www.douyin.com/search/" + title;

                    Map<String, String> metadata = new LinkedHashMap<>();
                    if (!label.isBlank()) metadata.put("标签", label);

                    // 热词关联视频数
                    int videoCount = node.path("video_count").asInt(0);
                    if (videoCount > 0) metadata.put("视频数", String.valueOf(videoCount));

                    items.add(new HotItem(
                            title, null, url,
                            CrawlSource.DOUYIN,
                            rank++, hotScore,
                            metadata, LocalDateTime.now()
                    ));
                }
            }

            long cost = System.currentTimeMillis() - start;
            log.info("抖音热搜采集完成：{} 条, 耗时 {}ms", items.size(), cost);
            return CrawlResult.success(CrawlSource.DOUYIN, items, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("抖音热搜采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.DOUYIN, e.getMessage(), cost);
        }
    }

    private String formatHotValue(long value) {
        if (value >= 100_000_000) {
            return String.format("%.1f 亿", value / 100_000_000.0);
        } else if (value >= 10_000) {
            return String.format("%.1f 万", value / 10_000.0);
        }
        return String.valueOf(value);
    }
}
