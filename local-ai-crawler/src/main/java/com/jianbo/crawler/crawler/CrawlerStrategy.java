package com.jianbo.crawler.crawler;

import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;

/**
 * 爬虫策略接口
 *
 * 所有爬虫（Jsoup / API / Playwright）实现此接口，
 * 由 CrawlPipelineService 统一调度和管理。
 *
 * 设计要点：
 *   - 每个实现类注册为 Spring Bean，通过 getSource() 标识来源
 *   - crawl() 内部自行处理异常，始终返回 CrawlResult（不抛出异常）
 *   - Pipeline 通过 List<CrawlerStrategy> 自动收集所有实现
 */
public interface CrawlerStrategy {

    /**
     * 执行一次采集
     *
     * @return 采集结果（成功/失败均封装为 CrawlResult）
     */
    CrawlResult crawl();

    /**
     * 标识本爬虫的数据来源
     *
     * @return 数据来源枚举
     */
    CrawlSource getSource();
}
