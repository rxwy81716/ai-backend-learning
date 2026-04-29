package com.jianbo.crawler.controller;

import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.repository.TaskLogRepository;
import com.jianbo.crawler.service.BloomFilterService;
import com.jianbo.crawler.service.CrawlPipelineService;
import com.jianbo.crawler.service.CrawlPipelineService.PipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 爬虫手动触发控制器（开发调试 + 运维管理用）
 *
 * 提供 REST API 手动触发采集任务，方便开发调试和运维操作。
 * 生产环境建议通过定时任务或 XXL-JOB 调度。
 *
 * 接口列表：
 *   GET  /api/crawler/sources              → 查看所有数据来源
 *   POST /api/crawler/execute/{source}     → 触发指定来源的完整流水线
 *   POST /api/crawler/execute-all          → 触发全量采集
 *   POST /api/crawler/crawl-only/{source}  → 仅采集不入库（调试用）
 *   GET  /api/crawler/stats                → 查看运行状态统计
 */
@Slf4j
@RestController
@RequestMapping("/api/crawler")
@RequiredArgsConstructor
public class CrawlerController {

    private final CrawlPipelineService pipelineService;
    private final List<CrawlerStrategy> crawlers;
    private final BloomFilterService bloomFilterService;
    private final TaskLogRepository taskLogRepository;

    /**
     * 查看所有已注册的数据来源
     */
    @GetMapping("/sources")
    public List<Map<String, String>> listSources() {
        return Arrays.stream(CrawlSource.values())
                .map(s -> Map.of(
                        "name", s.name(),
                        "displayName", s.getDisplayName(),
                        "crawlType", s.getCrawlType(),
                        "category", s.getCategory()
                ))
                .toList();
    }

    /**
     * 手动触发指定来源的完整流水线（采集 → 去重 → 清洗 → 入库）
     *
     * @param source 来源名称（如 GITHUB_TRENDING、WEIBO_HOT 等）
     */
    @PostMapping("/execute/{source}")
    public PipelineResult execute(@PathVariable String source) {
        CrawlSource crawlSource = CrawlSource.valueOf(source.toUpperCase());
        return pipelineService.execute(crawlSource, "MANUAL");
    }

    /**
     * 手动触发全量采集
     */
    @PostMapping("/execute-all")
    public List<PipelineResult> executeAll() {
        return pipelineService.executeAll("MANUAL");
    }

    /**
     * 仅执行采集，不经过流水线（开发调试用）
     *
     * 直接调用爬虫获取原始数据，不做去重/清洗/入库。
     */
    @PostMapping("/crawl-only/{source}")
    public CrawlResult crawlOnly(@PathVariable String source) {
        CrawlSource crawlSource = CrawlSource.valueOf(source.toUpperCase());
        CrawlerStrategy crawler = crawlers.stream()
                .filter(c -> c.getSource() == crawlSource)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未找到爬虫: " + source));
        return crawler.crawl();
    }

    /**
     * 查看运行状态统计
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "registeredCrawlers", crawlers.stream()
                        .map(c -> c.getSource().getDisplayName())
                        .toList(),
                "crawlerCount", crawlers.size(),
                "bloomFilterElementCount", bloomFilterService.approximateElementCount()
        );
    }

    // ==================== 任务日志查询 ====================

    /**
     * 查询最近任务执行日志
     *
     * @param limit 返回条数（默认50）
     */
    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(defaultValue = "50") int limit) {
        return taskLogRepository.findRecent(limit);
    }

    /**
     * 查询指定来源的最近任务日志
     */
    @GetMapping("/logs/{source}")
    public List<Map<String, Object>> logsBySource(
            @PathVariable String source,
            @RequestParam(defaultValue = "30") int limit) {
        return taskLogRepository.findRecentBySource(source.toUpperCase(), limit);
    }

    /**
     * 今日各来源执行统计
     */
    @GetMapping("/logs/today-stats")
    public List<Map<String, Object>> logsTodayStats() {
        return taskLogRepository.todayStats();
    }
}
