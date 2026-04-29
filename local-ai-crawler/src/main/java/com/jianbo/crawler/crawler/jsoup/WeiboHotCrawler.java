package com.jianbo.crawler.crawler.jsoup;

import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.UserAgentUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微博热搜爬虫（Jsoup 静态页面解析）
 *
 * 目标页面：https://s.weibo.com/top/summary
 * 采集内容：热搜标题、排名、热度值、标签（热/沸/新等）
 *
 * 注意事项：
 *   - 微博热搜页面可能需要 Cookie 才能正常访问
 *   - 如果被反爬拦截，考虑切换为 AJAX 接口：
 *     https://weibo.com/ajax/side/hotSearch
 *   - 页面结构可能变化，选择器需定期维护
 */
@Slf4j
@Component
public class WeiboHotCrawler implements CrawlerStrategy {

    /** 微博热搜页面地址 */
    private static final String TARGET_URL = "https://s.weibo.com/top/summary";
    /** 备用 AJAX 接口（返回 JSON，作为降级方案） */
    private static final String AJAX_URL = "https://weibo.com/ajax/side/hotSearch";
    private static final int TIMEOUT_MS = 15_000;

    @Override
    public CrawlSource getSource() {
        return CrawlSource.WEIBO_HOT;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集微博热搜 ...");

        try {
            // 优先尝试 HTML 页面解析
            List<HotItem> items = crawlFromHtml();

            // 如果 HTML 方式失败（可能被反爬），降级为 AJAX 接口
            if (items.isEmpty()) {
                log.warn("微博 HTML 页面解析为空，尝试 AJAX 接口降级 ...");
                items = crawlFromAjax();
            }

            long cost = System.currentTimeMillis() - start;
            log.info("微博热搜采集完成：{} 条, 耗时 {}ms", items.size(), cost);
            return CrawlResult.success(CrawlSource.WEIBO_HOT, items, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("微博热搜采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.WEIBO_HOT, e.getMessage(), cost);
        }
    }

    /**
     * 方式一：Jsoup 解析 HTML 热搜页面
     */
    private List<HotItem> crawlFromHtml() throws Exception {
        Document doc = Jsoup.connect(TARGET_URL)
                .userAgent(UserAgentUtil.randomDesktop())
                .timeout(TIMEOUT_MS)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://weibo.com/")
                .get();

        // 热搜表格行
        Elements rows = doc.select("table tbody tr");
        List<HotItem> items = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Element row = rows.get(i);

            // 排名（第一条为"置顶"，跳过）
            String rankText = row.select("td.td-01").text().trim();
            if (rankText.isEmpty() || rankText.equals("•")) continue;

            // 热搜标题
            Element linkEl = row.selectFirst("td.td-02 a");
            if (linkEl == null) continue;
            String title = linkEl.text().trim();
            if (title.isEmpty()) continue;

            // 搜索链接
            String href = linkEl.attr("href");
            String searchUrl = href.startsWith("http") ? href : "https://s.weibo.com" + href;

            // 热度值
            String hotScore = row.select("td.td-02 span").text().trim();

            // 热搜标签（热/沸/新/爆）
            String tag = row.select("td.td-03 i").text().trim();

            Map<String, String> metadata = new LinkedHashMap<>();
            if (!tag.isEmpty()) metadata.put("标签", tag);

            int rank;
            try {
                rank = Integer.parseInt(rankText);
            } catch (NumberFormatException e) {
                rank = i + 1;
            }

            items.add(new HotItem(
                    title, null, searchUrl,
                    CrawlSource.WEIBO_HOT,
                    rank, hotScore,
                    metadata, LocalDateTime.now()
            ));
        }

        return items;
    }

    /**
     * 方式二（降级）：通过 AJAX 接口获取热搜 JSON
     *
     * 接口返回格式：{"data":{"realtime":[{"word":"xxx","num":12345,...}]}}
     */
    private List<HotItem> crawlFromAjax() throws Exception {
        // 请求 AJAX 接口获取 JSON 字符串
        String json = Jsoup.connect(AJAX_URL)
                .userAgent(UserAgentUtil.randomDesktop())
                .timeout(TIMEOUT_MS)
                .header("Accept", "application/json")
                .header("Referer", "https://weibo.com/")
                .ignoreContentType(true)
                .execute()
                .body();

        // 简单解析 JSON（不引入额外依赖，使用 Jackson ObjectMapper）
        // 此处使用 Spring Boot 自带的 Jackson
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(json);
        var realtime = root.path("data").path("realtime");

        List<HotItem> items = new ArrayList<>();
        if (realtime.isArray()) {
            int rank = 1;
            for (var node : realtime) {
                String word = node.path("word").asText("");
                if (word.isBlank()) continue;

                long num = node.path("num").asLong(0);
                String labelName = node.path("label_name").asText("");
                String category = node.path("category").asText("");

                Map<String, String> metadata = new LinkedHashMap<>();
                if (!labelName.isEmpty()) metadata.put("标签", labelName);
                if (!category.isEmpty()) metadata.put("分类", category);

                items.add(new HotItem(
                        word, null,
                        "https://s.weibo.com/weibo?q=%23" + word + "%23",
                        CrawlSource.WEIBO_HOT,
                        rank++, String.valueOf(num),
                        metadata, LocalDateTime.now()
                ));
            }
        }

        return items;
    }
}
