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
 * B站热门视频爬虫（公开 JSON 接口）
 *
 * 接口地址：https://api.bilibili.com/x/web-interface/popular?ps=50&pn=1
 * 返回格式：{"code":0,"data":{"list":[{"title":"xxx","stat":{"view":123},...}]}}
 *
 * 优势：
 *   - B站公开 API，返回结构化 JSON
 *   - 包含播放量、弹幕数、点赞数等详细统计
 *   - 无需登录即可访问
 *
 * 说明：
 *   - ps=50 每页 50 条，pn=1 第一页
 *   - 也可使用排行榜接口：/x/web-interface/ranking/v2
 */
@Slf4j
@Component
public class BilibiliHotCrawler implements CrawlerStrategy {

    /** B站综合热门接口 */
    private static final String API_URL = "https://api.bilibili.com/x/web-interface/popular?ps=50&pn=1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient restClient;

    public BilibiliHotCrawler(@Qualifier("commonRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CrawlSource getSource() {
        return CrawlSource.BILIBILI_HOT;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集B站热门 ...");

        try {
            // 1. 请求B站热门 API
            String json = restClient.get()
                    .uri(API_URL)
                    .header("User-Agent", UserAgentUtil.randomDesktop())
                    .header("Referer", "https://www.bilibili.com/")
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);

            // 2. 解析 JSON
            JsonNode root = MAPPER.readTree(json);
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                String msg = root.path("message").asText("未知错误");
                throw new RuntimeException("B站 API 返回错误: code=" + code + ", msg=" + msg);
            }

            JsonNode list = root.path("data").path("list");
            List<HotItem> items = new ArrayList<>();

            if (list.isArray()) {
                int rank = 1;
                for (JsonNode video : list) {
                    // 视频标题
                    String title = video.path("title").asText("").trim();
                    if (title.isBlank()) continue;

                    // 视频描述
                    String desc = video.path("desc").asText("").trim();

                    // BV号 → 视频链接
                    String bvid = video.path("bvid").asText("");
                    String url = bvid.isEmpty() ? "" : "https://www.bilibili.com/video/" + bvid;

                    // UP主
                    String owner = video.path("owner").path("name").asText("");

                    // 统计数据
                    JsonNode stat = video.path("stat");
                    long viewCount = stat.path("view").asLong(0);
                    long danmaku = stat.path("danmaku").asLong(0);
                    long like = stat.path("like").asLong(0);
                    long reply = stat.path("reply").asLong(0);

                    // 分区名
                    String tname = video.path("tname").asText("");

                    // 推荐理由
                    String rcmdReason = video.path("rcmd_reason").path("content").asText("");

                    Map<String, String> metadata = new LinkedHashMap<>();
                    if (!owner.isEmpty()) metadata.put("UP主", owner);
                    if (!tname.isEmpty()) metadata.put("分区", tname);
                    metadata.put("播放量", formatCount(viewCount));
                    metadata.put("弹幕数", formatCount(danmaku));
                    metadata.put("点赞数", formatCount(like));
                    if (reply > 0) metadata.put("评论数", formatCount(reply));
                    if (!rcmdReason.isEmpty()) metadata.put("推荐理由", rcmdReason);

                    items.add(new HotItem(
                            title, desc, url,
                            CrawlSource.BILIBILI_HOT,
                            rank++, formatCount(viewCount),
                            metadata, LocalDateTime.now()
                    ));
                }
            }

            long cost = System.currentTimeMillis() - start;
            log.info("B站热门采集完成：{} 条, 耗时 {}ms", items.size(), cost);
            return CrawlResult.success(CrawlSource.BILIBILI_HOT, items, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("B站热门采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.BILIBILI_HOT, e.getMessage(), cost);
        }
    }

    /**
     * 格式化数字（如 12345 → "1.2万"）
     */
    private String formatCount(long count) {
        if (count >= 100_000_000) {
            return String.format("%.1f亿", count / 100_000_000.0);
        } else if (count >= 10_000) {
            return String.format("%.1f万", count / 10_000.0);
        }
        return String.valueOf(count);
    }
}
