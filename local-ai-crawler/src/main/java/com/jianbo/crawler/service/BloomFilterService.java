package com.jianbo.crawler.service;

import com.jianbo.crawler.model.HotItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * BloomFilter 布隆过滤器去重服务（Redisson 持久化版）
 *
 * 核心职责：
 *   - 判断某条热榜数据是否已被采集过
 *   - 对新数据标记为"已处理"，避免重复入库
 *
 * 去重策略：
 *   使用 HotItem.fingerprint()（来源+标题）作为指纹，
 *   写入 Redisson RBloomFilter 进行 O(1) 判重。
 *
 * 优势：
 *   - 基于 Redis 持久化，重启不丢失
 *   - 支持分布式部署，多实例共享去重状态
 *   - 存在极低误判率（约 1%），即极少量新数据可能被误判为已存在
 *   - 不支持删除已插入元素
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private final RedissonClient redissonClient;
    private RBloomFilter<String> crawlBloomFilter;

    private static final String BLOOM_FILTER_KEY = "crawler:bloomfilter";
    private static final long EXPECTED_INSERTIONS = 100_000; // 预期插入10万条
    private static final double FALSE_PROBABILITY = 0.01;     // 误判率1%

    @PostConstruct
    public void init() {
        // 获取或创建 BloomFilter（已存在则复用）
        crawlBloomFilter = redissonClient.getBloomFilter(BLOOM_FILTER_KEY);
        
        if (!crawlBloomFilter.isExists()) {
            crawlBloomFilter.tryInit(EXPECTED_INSERTIONS, FALSE_PROBABILITY);
            log.info("BloomFilter 初始化完成 | expectedInsertions={}, falseProbability={}", 
                    EXPECTED_INSERTIONS, FALSE_PROBABILITY);
        } else {
            log.info("BloomFilter 已存在，复用现有数据 | size={}", crawlBloomFilter.getSize());
        }
    }

    /**
     * 过滤已采集的条目，仅返回新数据
     *
     * @param items 原始采集列表
     * @return 去重后的新数据列表
     */
    public List<HotItem> filterDuplicates(List<HotItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<HotItem> newItems = items.stream()
                .filter(item -> !crawlBloomFilter.contains(item.fingerprint()))
                .toList();

        int duplicateCount = items.size() - newItems.size();
        if (duplicateCount > 0) {
            log.info("BloomFilter 去重：原始 {} 条 → 新增 {} 条，过滤重复 {} 条",
                    items.size(), newItems.size(), duplicateCount);
        }

        return newItems;
    }

    /**
     * 将已处理的条目标记到布隆过滤器
     *
     * @param items 已成功入库的条目
     */
    public void markAsProcessed(List<HotItem> items) {
        if (items == null) return;
        items.forEach(item -> crawlBloomFilter.add(item.fingerprint()));
        log.debug("BloomFilter 标记 {} 条已处理", items.size());
    }

    /**
     * 查询布隆过滤器的近似元素数量（用于监控）
     */
    public long approximateElementCount() {
        return crawlBloomFilter.getSize();
    }

    /**
     * 清空 BloomFilter（用于重置）
     */
    public void clear() {
        crawlBloomFilter.delete();
        init(); // 重新初始化
        log.info("BloomFilter 已清空并重新初始化");
    }
}
