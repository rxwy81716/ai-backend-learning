package com.jianbo.crawler.crawler.playwright;

import com.jianbo.crawler.config.CrawlerProperties;
import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.util.UserAgentUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 抖音热点爬虫（Playwright 无头浏览器）
 *
 * 目标页面：https://www.douyin.com/hot
 * 采集内容：热点话题标题、热度值、视频描述
 *
 * 为什么用 Playwright：
 *   - 抖音网页版完全依赖 JS 渲染，纯 HTTP 请求无法获取内容
 *   - 页面有复杂的反爬机制（签名校验、Cookie 验证等）
 *   - 需要模拟浏览器环境绕过基础检测
 *
 * 注意事项：
 *   - 抖音反爬非常严格，高频采集必然被封
 *   - 建议采集频率 ≤ 每4小时一次
 *   - 如被检测到，页面可能返回空内容或验证码
 *   - 首次运行需安装浏览器：mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DouyinCrawler implements CrawlerStrategy {

    private static final String TARGET_URL = "https://www.douyin.com/hot";

    private final CrawlerProperties props;

    @Override
    public CrawlSource getSource() {
        return CrawlSource.DOUYIN;
    }

    @Override
    public CrawlResult crawl() {
        long start = System.currentTimeMillis();
        log.info("开始采集抖音热点（Playwright）...");

        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions()
                            .setHeadless(props.playwright().headless())
                            .setTimeout(props.playwright().timeout())
            );

            BrowserContext context = browser.newContext(
                    new Browser.NewContextOptions()
                            .setUserAgent(UserAgentUtil.randomDesktop())
                            .setViewportSize(1920, 1080)
                            .setLocale("zh-CN")
            );

            Page page = context.newPage();

            try {
                // 导航到抖音热榜页
                page.navigate(TARGET_URL, new Page.NavigateOptions()
                        .setTimeout(props.playwright().timeout())
                        .setWaitUntil(WaitUntilState.NETWORKIDLE));

                // 等待热榜列表加载
                page.waitForSelector("[class*='hot-list'], [class*='hotlist'], .hot-item-wrapper",
                        new Page.WaitForSelectorOptions().setTimeout(15_000));

                // 滚动加载更多
                for (int i = 0; i < 2; i++) {
                    page.mouse().wheel(0, 600);
                    page.waitForTimeout(1500);
                }

                // 提取热榜数据
                List<HotItem> items = extractHotList(page);

                long cost = System.currentTimeMillis() - start;
                log.info("抖音热点采集完成：{} 条, 耗时 {}ms", items.size(), cost);
                return CrawlResult.success(CrawlSource.DOUYIN, items, cost);

            } finally {
                context.close();
                browser.close();
            }

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            log.error("抖音热点采集失败：{}", e.getMessage(), e);
            return CrawlResult.fail(CrawlSource.DOUYIN, e.getMessage(), cost);
        }
    }

    /**
     * 从页面提取抖音热榜数据
     *
     * 抖音热榜页面结构（class 名称含 hash 值，需用模糊匹配）：
     *   - 每条热点是一个列表项
     *   - 包含排名序号、话题标题、热度值、视频封面
     */
    private List<HotItem> extractHotList(Page page) {
        List<HotItem> items = new ArrayList<>();

        // 尝试多种选择器适配不同版本的抖音页面
        Locator hotItems = page.locator(
                "[class*='hot-list-item'], [class*='hotlist-item'], .hot-item-wrapper > div"
        );

        int count = hotItems.count();
        log.debug("抖音热榜检测到 {} 个热点元素", count);

        for (int i = 0; i < Math.min(count, 50); i++) {
            try {
                Locator item = hotItems.nth(i);

                // 排名序号
                String rankText = safeText(item, "[class*='rank'], [class*='index'], .rank-num");
                int rank;
                try {
                    rank = Integer.parseInt(rankText.replaceAll("[^0-9]", ""));
                } catch (NumberFormatException e) {
                    rank = i + 1;
                }

                // 热点标题
                String title = safeText(item, "[class*='title'], [class*='word'], .hot-title, a span");
                if (title.isBlank()) continue;

                // 热度值
                String hotScore = safeText(item, "[class*='hot-value'], [class*='count'], .hot-score");

                // 热点链接
                String href = safeAttr(item, "a", "href");
                String url = href.isEmpty() ? ""
                        : href.startsWith("http") ? href : "https://www.douyin.com" + href;

                // 标签（如"热" "新" "飙升"）
                String tag = safeText(item, "[class*='tag'], [class*='label'], .hot-tag");

                Map<String, String> metadata = new LinkedHashMap<>();
                if (!tag.isEmpty()) metadata.put("标签", tag);

                items.add(new HotItem(
                        title, null, url,
                        CrawlSource.DOUYIN,
                        rank, hotScore,
                        metadata, LocalDateTime.now()
                ));

            } catch (Exception e) {
                log.debug("抖音热点 #{} 解析失败：{}", i, e.getMessage());
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
