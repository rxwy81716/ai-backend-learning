package com.jianbo.crawler.crawler.playwright;

import com.jianbo.crawler.config.CrawlerProperties;
import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.UserAgentUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 小红书热门爬虫（Playwright 无头浏览器）
 *
 * 目标页面：https://www.xiaohongshu.com/explore
 * 采集方式：Playwright 渲染 JS 动态页面后提取笔记卡片信息
 *
 * 为什么用 Playwright：
 *   - 小红书页面内容由 JS 动态渲染，Jsoup 无法获取
 *   - 需要模拟真实浏览器行为（滚动加载、Cookie 等）
 *   - Playwright 支持拦截请求、注入脚本等高级特性
 *
 * 注意事项：
 *   - 首次运行需安装浏览器：mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
 *   - 小红书反爬较严，频繁访问可能触发验证码
 *   - 每次采集创建独立 Playwright 实例，保证线程安全
 */
@Slf4j
@Component
public class XiaohongshuCrawler implements CrawlerStrategy {

    private static final String TARGET_URL = "https://www.xiaohongshu.com/explore";

    private final Browser browser;
    private final CrawlerProperties props;

    public XiaohongshuCrawler(@Lazy Browser browser, CrawlerProperties props) {
        this.browser = browser;
        this.props = props;
    }

    @Override
    public CrawlSource getSource() {
        return CrawlSource.XIAOHONGSHU;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集小红书热门（Playwright）...");

        BrowserContext context = null;
        try {
            context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(UserAgentUtil.randomDesktop())
                            .setViewportSize(1920, 1080)
                            .setLocale("zh-CN")
            );

            Page page = context.newPage();

            // 导航到小红书探索页
            page.navigate(TARGET_URL, new Page.NavigateOptions()
                    .setTimeout(props.playwright().timeout())
                    .setWaitUntil(WaitUntilState.NETWORKIDLE));

            // 等待笔记卡片加载完成
            page.waitForSelector(".note-item", new Page.WaitForSelectorOptions()
                    .setTimeout(10_000));

            // 向下滚动以加载更多内容
            for (int i = 0; i < 3; i++) {
                page.mouse().wheel(0, 800);
                page.waitForTimeout(1500);
            }

            // 使用 Locator API 提取笔记卡片
            List<HotItem> hotItems = extractNotesFromPage(page);

            long cost = System.currentTimeMillis() - start;
            log.info("小红书热门采集完成：{} 条, 耗时 {}ms", hotItems.size(), cost);
            return CrawlResult.success(CrawlSource.XIAOHONGSHU, hotItems, cost);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("小红书热门采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.XIAOHONGSHU, e.getMessage(), cost);
        } finally {
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 从页面提取笔记数据（使用 Locator API，更稳定）
     */
    private List<HotItem> extractNotesFromPage(Page page) {
        List<HotItem> items = new ArrayList<>();

        // 使用多种选择器兼容不同版本页面结构
        Locator noteCards = page.locator("section.note-item, div.note-item, a.cover");
        int count = noteCards.count();

        for (int i = 0; i < Math.min(count, 50); i++) {
            try {
                Locator card = noteCards.nth(i);

                // 标题：尝试多种选择器
                String title = safeText(card, ".title, a span, .note-text");
                if (title.isBlank()) continue;

                // 作者
                String author = safeText(card, ".author-wrapper .name, .author .name, .user-name");

                // 点赞数
                String likes = safeText(card, ".like-wrapper .count, .engagement .count, .like-count");

                // 链接
                String href = safeAttr(card, "a", "href");
                String url = href.startsWith("http") ? href : "https://www.xiaohongshu.com" + href;

                Map<String, String> metadata = new LinkedHashMap<>();
                if (!author.isEmpty()) metadata.put("作者", author);
                if (!likes.isEmpty()) metadata.put("点赞", likes);

                items.add(new HotItem(
                        title, null, url,
                        CrawlSource.XIAOHONGSHU,
                        i + 1, likes,
                        metadata, LocalDateTime.now()
                ));
            } catch (Exception e) {
                log.debug("小红书卡片 #{} 解析失败：{}", i, e.getMessage());
            }
        }

        return items;
    }

    /** 安全获取文本内容 */
    private String safeText(Locator parent, String selector) {
        try {
            Locator el = parent.locator(selector).first();
            if (el.count() > 0) {
                return el.textContent().trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /** 安全获取属性值 */
    private String safeAttr(Locator parent, String selector, String attr) {
        try {
            Locator el = parent.locator(selector).first();
            if (el.count() > 0) {
                String val = el.getAttribute(attr);
                return val != null ? val.trim() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }
}
