package com.jianbo.crawler.service;

import com.jianbo.crawler.crawler.CrawlerStrategy;
import com.jianbo.crawler.model.CrawlResult;
import com.jianbo.crawler.model.CrawlSource;
import com.jianbo.crawler.model.HotItem;
import com.jianbo.crawler.repository.HotItemRepository;
import com.jianbo.crawler.repository.TaskLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 爬虫数据处理流水线（Pipeline 核心调度器）
 *
 * 完整流程：
 *   1. 采集（Crawler）       → 调用指定爬虫获取原始数据
 *   2. 去重（BloomFilter）   → 过滤已采集过的重复条目
 *   3. 清洗（DataClean）     → 文本标准化、长度裁剪、无效过滤
 *   4. 持久化（DB）          → 热榜数据保存到 PostgreSQL（每日统计用）
 *   5. 结构化（buildDoc）    → 组装为结构化文档文本
 *   6. 入库（KnowledgeAPI）  → 上传至 knowledge 服务完成向量化存储
 *
 * 设计特点：
 *   - 所有 CrawlerStrategy 实现自动注入，通过 getSource() 索引
 *   - 每个步骤独立可测，Pipeline 负责编排
 *   - 异常不阻断：单个爬虫失败不影响其他爬虫
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlPipelineService {

    /** 所有爬虫实现（Spring 自动收集） */
    private final List<CrawlerStrategy> crawlers;
    private final BloomFilterService bloomFilterService;
    private final DataCleanService dataCleanService;
    private final HotItemRepository hotItemRepository;
//    private final KnowledgeApiService knowledgeApiService;
    private final TaskLogRepository taskLogRepository;

    /** 并发控制信号量（最多同时执行3个爬虫） */
    private final Semaphore concurrencySemaphore = new Semaphore(3);

    /** 按来源索引的爬虫 Map（懒加载） */
    private volatile Map<CrawlSource, CrawlerStrategy> crawlerMap;

    /**
     * 获取爬虫 Map（按来源索引）
     */
    private Map<CrawlSource, CrawlerStrategy> getCrawlerMap() {
        if (crawlerMap == null) {
            synchronized (this) {
                if (crawlerMap == null) {
                    crawlerMap = crawlers.stream()
                            .collect(Collectors.toMap(
                                    CrawlerStrategy::getSource,
                                    Function.identity()
                            ));
                    log.info("已注册 {} 个爬虫：{}", crawlerMap.size(),
                            crawlerMap.keySet().stream()
                                    .map(CrawlSource::getDisplayName)
                                    .collect(Collectors.joining(", ")));
                }
            }
        }
        return crawlerMap;
    }

    /**
     * 执行指定来源的完整流水线（定时调度调用）
     */
    public PipelineResult execute(CrawlSource source) {
        return execute(source, "SCHEDULED");
    }

    /**
     * 执行指定来源的完整流水线
     *
     * @param source      数据来源
     * @param triggerType 触发方式：SCHEDULED / MANUAL
     * @return 流水线执行结果摘要
     */
    public PipelineResult execute(CrawlSource source, String triggerType) {
        CrawlerStrategy crawler = getCrawlerMap().get(source);
        if (crawler == null) {
            log.error("未找到来源 [{}] 对应的爬虫实现", source.getDisplayName());
            PipelineResult fail = new PipelineResult(source, false, 0, 0, 0, triggerType, "爬虫未注册");
            saveLog(fail);
            return fail;
        }

        log.info("▶ 流水线启动：{}", source.getDisplayName());
        long pipelineStart = System.currentTimeMillis();

        try {
            // ===== Step 1: 采集 =====
            CrawlResult crawlResult = crawler.crawl();
            if (!crawlResult.success() || crawlResult.items().isEmpty()) {
                String msg = crawlResult.success() ? "采集结果为空" : crawlResult.errorMsg();
                log.warn("  ✗ 采集失败或为空：{}", msg);
                long cost = System.currentTimeMillis() - pipelineStart;
                PipelineResult fail = new PipelineResult(source, false, 0, 0, cost, triggerType, msg);
                saveLog(fail);
                return fail;
            }
            log.info("  ✓ 采集完成：{} 条", crawlResult.items().size());

            // ===== Step 2: BloomFilter 去重 =====
            List<HotItem> newItems = bloomFilterService.filterDuplicates(crawlResult.items());
            if (newItems.isEmpty()) {
                log.info("  ✓ 全部为重复数据，跳过后续步骤");
                long cost0 = System.currentTimeMillis() - pipelineStart;
                PipelineResult dup = new PipelineResult(source, true, crawlResult.items().size(), 0, cost0, triggerType, "全部重复");
                saveLog(dup);
                return dup;
            }
            log.info("  ✓ 去重完成：新增 {} 条", newItems.size());

            // ===== Step 3: 数据清洗 =====
            List<HotItem> cleanedItems = dataCleanService.cleanAll(newItems);
            if (cleanedItems.isEmpty()) {
                log.info("  ✓ 清洗后无有效数据");
                long cost1 = System.currentTimeMillis() - pipelineStart;
                PipelineResult empty = new PipelineResult(source, true, crawlResult.items().size(), 0, cost1, triggerType, "清洗后为空");
                saveLog(empty);
                return empty;
            }
            log.info("  ✓ 清洗完成：有效 {} 条", cleanedItems.size());

            // ===== Step 4: 持久化到数据库（每日热榜统计） =====
            int dbInserted = hotItemRepository.batchSave(cleanedItems, source);
            log.info("  ✓ 数据库持久化：新增 {} 条", dbInserted);

            // ===== Step 5: 标记已处理（写入布隆过滤器） =====
            // 热榜数据只存 hot_items 表，不入知识库向量化
            // （热榜是高频时效数据，入 RAG 会干扰正常问答且检索效果差）
            bloomFilterService.markAsProcessed(cleanedItems);

            long totalCost = System.currentTimeMillis() - pipelineStart;
            log.info("◆ 流水线完成：{} | 采集 {} → 新增 {} → 入库 | 耗时 {}ms",
                    source.getDisplayName(), crawlResult.items().size(), cleanedItems.size(), totalCost);

            PipelineResult ok = new PipelineResult(source, true, crawlResult.items().size(), cleanedItems.size(), totalCost, triggerType, null);
            saveLog(ok);
            return ok;

        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - pipelineStart;
            log.error("✗ 流水线异常：{} | 耗时 {}ms | 错误：{}",
                    source.getDisplayName(), totalCost, e.getMessage(), e);
            PipelineResult err = new PipelineResult(source, false, 0, 0, totalCost, triggerType, e.getMessage());
            saveLog(err);
            return err;
        }
    }

    /**
     * 执行所有爬虫的完整流水线
     */
    public List<PipelineResult> executeAll() {
        return executeAll("SCHEDULED");
    }

    public List<PipelineResult> executeAll(String triggerType) {
        log.info("========== 全量爬虫流水线启动 ==========");
        List<PipelineResult> results = getCrawlerMap().keySet().stream()
                .map(s -> {
                    try {
                        concurrencySemaphore.acquire();
                        return execute(s, triggerType);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new PipelineResult(s, false, 0, 0, 0, triggerType, "任务被中断");
                    } finally {
                        concurrencySemaphore.release();
                    }
                })
                .toList();

        long successCount = results.stream().filter(PipelineResult::success).count();
        log.info("========== 全量流水线完成：成功 {}/{} ==========", successCount, results.size());
        return results;
    }

    /**
     * 流水线执行结果摘要
     *
     * @param source       数据来源
     * @param success      是否成功
     * @param crawledCount 采集到的原始条数
     * @param storedCount  最终入库条数
     * @param errorMsg     错误信息
     */
    public record PipelineResult(
            CrawlSource source,
            boolean success,
            int crawledCount,
            int storedCount,
            long costMs,
            String triggerType,
            String errorMsg
    ) {}

    private void saveLog(PipelineResult result) {
        try {
            taskLogRepository.save(
                    result.source().name(),
                    result.triggerType(),
                    result.success(),
                    result.crawledCount(),
                    result.storedCount(),
                    result.costMs(),
                    result.errorMsg()
            );
        } catch (Exception e) {
            log.warn("保存任务日志失败：{}", e.getMessage());
        }
    }
}
