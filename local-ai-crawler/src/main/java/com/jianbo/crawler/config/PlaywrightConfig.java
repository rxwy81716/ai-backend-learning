package com.jianbo.crawler.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Playwright 单例配置
 *
 * 整个应用生命周期只初始化一次 Playwright 实例和 Browser 实例，
 * 避免每次采集都重新下载/检查浏览器二进制文件。
 *
 * 各爬虫使用时只需创建 BrowserContext（轻量级、线程隔离）。
 */
@Slf4j
@Configuration
public class PlaywrightConfig {

    private Playwright playwright;
    private Browser browser;

    @Bean
    @Lazy
    public Playwright playwright() {
        log.info("初始化 Playwright 实例（仅一次）...");
        this.playwright = Playwright.create();
        return this.playwright;
    }

    @Bean
    @Lazy
    public Browser playwrightBrowser(Playwright playwright, CrawlerProperties props) {
        log.info("启动 Chromium 浏览器实例（仅一次）...");
        this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(props.playwright().headless())
                        .setTimeout(props.playwright().timeout())
        );
        return this.browser;
    }

    @PreDestroy
    public void destroy() {
        if (browser != null) {
            try {
                browser.close();
                log.info("Chromium 浏览器已关闭");
            } catch (Exception e) {
                log.warn("关闭浏览器异常：{}", e.getMessage());
            }
        }
        if (playwright != null) {
            try {
                playwright.close();
                log.info("Playwright 已关闭");
            } catch (Exception e) {
                log.warn("关闭 Playwright 异常：{}", e.getMessage());
            }
        }
    }
}
