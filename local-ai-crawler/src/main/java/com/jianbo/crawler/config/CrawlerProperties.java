package com.jianbo.crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 爬虫全局配置属性（绑定 application.yaml 中 crawler.* 前缀）
 *
 * 使用 JDK16+ record 实现不可变配置绑定（Spring Boot 3.x+ 构造器绑定）
 */
@ConfigurationProperties(prefix = "crawler")
public record CrawlerProperties(
        /** local-ai-knowledge 服务连接配置 */
        KnowledgeApi knowledgeApi,
        /** 布隆过滤器配置 */
        BloomFilterProps bloomFilter,
        /** Playwright 无头浏览器配置 */
        PlaywrightProps playwright,
        /** 各爬虫定时调度 Cron 表达式 */
        ScheduleProps schedule
) {

    /** 知识库服务 API 配置 */
    public record KnowledgeApi(
            /** 服务基地址，如 http://localhost:12116 */
            String baseUrl,
            /** crawler-upload 接口认证 API Key */
            String apiKey
    ) {}

    /** 布隆过滤器配置 */
    public record BloomFilterProps(
            /** 预期最大插入数量 */
            int expectedInsertions,
            /** 误判率（false positive probability） */
            double fpp
    ) {}

    /** Playwright 配置 */
    public record PlaywrightProps(
            /** 是否无头模式 */
            boolean headless,
            /** 页面加载超时毫秒数 */
            int timeout
    ) {}

    /** 定时任务 Cron 表达式配置 */
    public record ScheduleProps(
            String githubTrending,
            String weiboHot,
            String zhihuHot,
            String bilibiliHot,
            String xiaohongshu,
            String douyin
    ) {}
}
