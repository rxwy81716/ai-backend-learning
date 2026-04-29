package com.jianbo.crawler.service;

import com.google.common.hash.BloomFilter;
import com.jianbo.crawler.model.HotItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * BloomFilter 布隆过滤器去重服务
 *
 * 核心职责：
 *   - 判断某条热榜数据是否已被采集过
 *   - 对新数据标记为"已处理"，避免重复入库
 *
 * 去重策略：
 *   使用 HotItem.fingerprint()（来源+标题）作为指纹，
 *   写入 Guava BloomFilter 进行 O(1) 判重。
 *
 * 局限性：
 *   - 进程内存储，重启后去重状态丢失（可升级为 Redisson RBloomFilter 持久化）
 *   - 存在极低误判率（约 0.1%），即极少量新数据可能被误判为已存在
 *   - 不支持删除已插入元素
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private final BloomFilter<String> crawlBloomFilter;

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
                .filter(item -> !crawlBloomFilter.mightContain(item.fingerprint()))
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
        items.forEach(item -> crawlBloomFilter.put(item.fingerprint()));
        log.debug("BloomFilter 标记 {} 条已处理", items.size());
    }

    /**
     * 查询布隆过滤器的近似元素数量（用于监控）
     */
    public long approximateElementCount() {
        return crawlBloomFilter.approximateElementCount();
    }
}
