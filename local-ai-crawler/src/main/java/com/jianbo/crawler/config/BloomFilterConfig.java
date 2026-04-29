package com.jianbo.crawler.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * BloomFilter 布隆过滤器配置
 *
 * 用于爬虫数据去重：通过 HotItem.fingerprint() 生成指纹，
 * 写入布隆过滤器判断是否已采集过，避免重复入库。
 *
 * 特性：
 *   - 空间效率极高（百万条目仅占约 1.7MB 内存）
 *   - 存在极低误判率（fpp=0.001 即 0.1%），但绝不漏判
 *   - 本实例为进程内单机版；分布式场景可替换为 Redisson RBloomFilter
 */
@Slf4j
@Configuration
public class BloomFilterConfig {

    @Bean
    public BloomFilter<String> crawlBloomFilter(CrawlerProperties props) {
        int expectedInsertions = props.bloomFilter().expectedInsertions();
        double fpp = props.bloomFilter().fpp();

        BloomFilter<String> filter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );

        log.info("BloomFilter 初始化完成：expectedInsertions={}, fpp={}", expectedInsertions, fpp);
        return filter;
    }
}
