package com.jianbo.crawler.job;

import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.service.CrawlPipelineService;
import com.jianbo.crawler.service.CrawlPipelineService.PipelineResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * XXL-JOB 分布式任务调度 Handler
 *
 * 适用场景：
 *   - 多实例部署需要分布式调度（避免重复执行）
 *   - 需要可视化任务管理界面（查看执行日志、手动触发、暂停/恢复）
 *   - 需要任务失败重试、报警通知等企业级特性
 *
 * 使用前提：
 *   1. 部署 xxl-job-admin 调度中心
 *   2. 配置 xxl.job.enabled=true
 *   3. 在调度中心新建执行器（appname=local-ai-crawler）
 *   4. 在调度中心新建任务，填写对应的 @XxlJob value
 *
 * 任务列表（在 xxl-job-admin 中配置）：
 *   - crawlerGithubTrending  → GitHub Trending
 *   - crawlerWeiboHot        → 微博热搜
 *   - crawlerZhihuHot        → 知乎热榜
 *   - crawlerBilibiliHot     → B站热门
 *   - crawlerXiaohongshu     → 小红书
 *   - crawlerDouyin          → 抖音
 *   - crawlerAll             → 全量采集
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")
public class CrawlerXxlJobHandler {

    private final CrawlPipelineService pipelineService;

    @XxlJob("crawlerGithubTrending")
    public void githubTrendingJob() {
        log.info("[XXL-JOB] 触发 GitHub Trending 采集");
        PipelineResult result = pipelineService.execute(CrawlSource.GITHUB_TRENDING);
        logResult(result);
    }

    @XxlJob("crawlerWeiboHot")
    public void weiboHotJob() {
        log.info("[XXL-JOB] 触发微博热搜采集");
        PipelineResult result = pipelineService.execute(CrawlSource.WEIBO_HOT);
        logResult(result);
    }

    @XxlJob("crawlerZhihuHot")
    public void zhihuHotJob() {
        log.info("[XXL-JOB] 触发知乎热榜采集");
        PipelineResult result = pipelineService.execute(CrawlSource.ZHIHU_HOT);
        logResult(result);
    }

    @XxlJob("crawlerBilibiliHot")
    public void bilibiliHotJob() {
        log.info("[XXL-JOB] 触发B站热门采集");
        PipelineResult result = pipelineService.execute(CrawlSource.BILIBILI_HOT);
        logResult(result);
    }

    @XxlJob("crawlerXiaohongshu")
    public void xiaohongshuJob() {
        log.info("[XXL-JOB] 触发小红书采集（Playwright）");
        PipelineResult result = pipelineService.execute(CrawlSource.XIAOHONGSHU);
        logResult(result);
    }

    @XxlJob("crawlerDouyin")
    public void douyinJob() {
        log.info("[XXL-JOB] 触发抖音采集（Playwright）");
        PipelineResult result = pipelineService.execute(CrawlSource.DOUYIN);
        logResult(result);
    }

    /**
     * 全量采集任务（一次性触发所有爬虫）
     * 在 xxl-job-admin 中手动触发时使用
     */
    @XxlJob("crawlerAll")
    public void allJob() {
        log.info("[XXL-JOB] 触发全量采集");
        List<PipelineResult> results = pipelineService.executeAll();
        results.forEach(this::logResult);
    }

    /** 打印执行结果到 XXL-JOB 日志 */
    private void logResult(PipelineResult result) {
        if (result.success()) {
            com.xxl.job.core.context.XxlJobHelper.log(
                    "[%s] 成功：采集 %d 条 → 入库 %d 条",
                    result.source().getDisplayName(),
                    result.crawledCount(),
                    result.storedCount());
        } else {
            com.xxl.job.core.context.XxlJobHelper.log(
                    "[%s] 失败：%s",
                    result.source().getDisplayName(),
                    result.errorMsg());
            // 任务标记失败，触发 XXL-JOB 重试/报警机制
            com.xxl.job.core.context.XxlJobHelper.handleFail(
                    result.source().getDisplayName() + " 采集失败: " + result.errorMsg());
        }
    }
}
