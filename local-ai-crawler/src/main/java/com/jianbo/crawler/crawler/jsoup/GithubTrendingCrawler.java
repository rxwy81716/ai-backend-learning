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
 * GitHub Trending 爬虫（Jsoup 静态页面解析）
 *
 * 目标页面：https://github.com/trending
 * 采集内容：仓库名、描述、编程语言、Star 数、Fork 数、今日新增 Star
 *
 * 注意事项：
 *   - GitHub Trending 页面结构可能随时变化，选择器需定期维护
 *   - 添加合理的请求间隔，避免触发 GitHub 速率限制
 */
@Slf4j
@Component
public class GithubTrendingCrawler implements CrawlerStrategy {

    private static final String TARGET_URL = "https://github.com/trending";
    private static final int TIMEOUT_MS = 15_000;

    @Override
    public CrawlSource getSource() {
        return CrawlSource.GITHUB_TRENDING;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集 GitHub Trending ...");

        try {
            // 1. 发起 HTTP 请求获取页面
            Document doc = Jsoup.connect(TARGET_URL)
                    .userAgent(UserAgentUtil.randomDesktop())
                    .timeout(TIMEOUT_MS)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .get();

            // 2. 解析仓库列表（每个仓库是一个 article.Box-row）
            Elements repos = doc.select("article.Box-row");
            List<HotItem> items = new ArrayList<>();

            for (int i = 0; i < repos.size(); i++) {
                Element repo = repos.get(i);

                // 仓库全名（owner/repo）
                String repoName = repo.select("h2 a").text().replaceAll("\\s+", "").trim();
                // 仓库描述
                String desc = repo.select("p.col-9").text().trim();
                // 仓库链接
                String href = repo.select("h2 a").attr("href").trim();
                String repoUrl = href.startsWith("http") ? href : "https://github.com" + href;
                // 编程语言
                String language = repo.select("span[itemprop=programmingLanguage]").text().trim();
                // Star 总数
                String totalStars = extractLinkText(repo, "a[href$='/stargazers']");
                // Fork 总数
                String totalForks = extractLinkText(repo, "a[href$='/forks']");
                // 今日新增 Star
                String todayStars = repo.select("span.d-inline-block.float-sm-right").text().trim();

                // 组装扩展元数据
                Map<String, String> metadata = new LinkedHashMap<>();
                if (!language.isEmpty()) metadata.put("语言", language);
                if (!totalStars.isEmpty()) metadata.put("总Star", totalStars);
                if (!totalForks.isEmpty()) metadata.put("总Fork", totalForks);
                if (!todayStars.isEmpty()) metadata.put("今日新增", todayStars);

                items.add(new HotItem(
                        repoName, desc, repoUrl,
                        CrawlSource.GITHUB_TRENDING,
                        i + 1, todayStars,
                        metadata, LocalDateTime.now()
                ));
            }

            long cost = System.currentTimeMillis() - start;
            log.info("GitHub Trending 采集完成：{} 条, 耗时 {}ms", items.size(), cost);
            return CrawlResult.success(CrawlSource.GITHUB_TRENDING, items, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("GitHub Trending 采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.GITHUB_TRENDING, e.getMessage(), cost);
        }
    }

    /**
     * 安全提取链接文本（防止 NPE）
     */
    private String extractLinkText(Element parent, String cssQuery) {
        Element el = parent.selectFirst(cssQuery);
        return el != null ? el.text().trim() : "";
    }
}
