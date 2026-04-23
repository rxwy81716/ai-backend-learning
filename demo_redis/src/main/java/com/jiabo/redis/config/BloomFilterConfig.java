package com.jiabo.redis.config;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Configuration
public class BloomFilterConfig {
    // 布隆过滤器：预计10万数据，误判率0.01（1%）
    @Bean
    public BloomFilter<String> bloomFilter() {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                100000,
                0.01
        );
    }
}
