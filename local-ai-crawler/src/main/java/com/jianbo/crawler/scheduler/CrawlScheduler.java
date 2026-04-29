package com.jianbo.crawler.scheduler;

import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.service.CrawlPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring 原生 @Scheduled 定时调度器
 *
 * 适用场景：单机部署、简单调度、无需可视化管理
 *
 * 调度策略：
 *   - GitHub Trending ：每 2 小时采集一次（技术趋势变化慢）
 *   - 微博热搜        ：每 30 分钟（社会热点变化快）
 *   - 知乎热榜        ：每 30 分钟
 *   - B站热门         ：每 3 小时（视频热度更新周期长）
 *   - 小红书           ：每 4 小时（动态渲染消耗大，降低频率）
 *   - 抖音             ：每 4 小时（同上，且反爬严格）
 *
 * Cron 表达式在 application.yaml 中配置，支持运行时调整。
 *
 * 注意：
 *   - 若同时启用 XXL-JOB（xxl.job.enabled=true），应关闭此调度器避免重复执行
 *   - 每个任务独立执行，单个失败不影响其他任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlScheduler {

    private final CrawlPipelineService pipelineService;

    /**
     * GitHub Trending 定时采集
     */
    @Scheduled(cron = "${crawler.schedule.github-trending}")
    public void scheduleGithubTrending() {
        log.info("[Scheduled] 触发 GitHub Trending 采集任务");
        pipelineService.execute(CrawlSource.GITHUB_TRENDING);
    }

    /**
     * 微博热搜定时采集
     */
    @Scheduled(cron = "${crawler.schedule.weibo-hot}")
    public void scheduleWeiboHot() {
        log.info("[Scheduled] 触发微博热搜采集任务");
        pipelineService.execute(CrawlSource.WEIBO_HOT);
    }

    /**
     * 知乎热榜定时采集
     */
    @Scheduled(cron = "${crawler.schedule.zhihu-hot}")
    public void scheduleZhihuHot() {
        log.info("[Scheduled] 触发知乎热榜采集任务");
        pipelineService.execute(CrawlSource.ZHIHU_HOT);
    }

    /**
     * B站热门定时采集
     */
    @Scheduled(cron = "${crawler.schedule.bilibili-hot}")
    public void scheduleBilibiliHot() {
        log.info("[Scheduled] 触发B站热门采集任务");
        pipelineService.execute(CrawlSource.BILIBILI_HOT);
    }

    /**
     * 小红书热门定时采集（Playwright）
     */
    @Scheduled(cron = "${crawler.schedule.xiaohongshu}")
    public void scheduleXiaohongshu() {
        log.info("[Scheduled] 触发小红书热门采集任务（Playwright）");
        pipelineService.execute(CrawlSource.XIAOHONGSHU);
    }

    /**
     * 抖音热点定时采集（Playwright）
     */
    @Scheduled(cron = "${crawler.schedule.douyin}")
    public void scheduleDouyin() {
        log.info("[Scheduled] 触发抖音热点采集任务（Playwright）");
        pipelineService.execute(CrawlSource.DOUYIN);
    }
}
