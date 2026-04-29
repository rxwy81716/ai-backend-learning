package com.jianbo.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI 知识库爬虫数据采集服务
 *
 * 定时采集多源热榜数据，经清洗去重后推送至 local-ai-knowledge 服务完成向量化入库。
 *
 * 采集方式：
 *   - 静态页面（Jsoup）：GitHub Trending、微博热搜
 *   - 公开接口（JSON）：知乎热榜、B站热门
 *   - 动态渲染（Playwright）：小红书、抖音
 *
 * 调度策略：
 *   - 简单场景：Spring 原生 @Scheduled
 *   - 分布式/复杂场景：XXL-JOB（可选启用）
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class LocalAiCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalAiCrawlerApplication.class, args);
    }
}
