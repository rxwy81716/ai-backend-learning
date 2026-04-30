package com.jianbo.localaiknowledge.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.ai.document.Document;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存配置
 *
 * <p>缓存项：
 *
 * <ul>
 *   <li>systemPrompt: SystemPrompt 配置（10 分钟过期，避免每次请求查 DB）
 *   <li>ragSearchCache: Hybrid 检索结果（60s 过期，重复提问/重试直接命中，规避 bge-m3 单次 2~3s 的 embedding 推理开销）
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public CacheManager cacheManager() {
    CaffeineCacheManager manager = new CaffeineCacheManager();
    manager.setCaffeine(
        Caffeine.newBuilder().maximumSize(100).expireAfterWrite(10, TimeUnit.MINUTES));
    return manager;
  }

  /**
   * RAG 检索结果缓存
   *
   * <p>Key: query + userId + topK 拼成的字符串；Value: 融合后的 Document 列表。
   *
   * <p>TTL 10 分钟，主要规避 bge-m3 单次 ~2.7s 的 embedding 推理；新文档入库 / 删除时
   * {@code DocumentParseService} 会主动 {@code invalidateAll()}，因此不会出现
   * "刚上传的文档检索不到" 的情况。
   */
  @Bean
  public Cache<String, List<Document>> ragSearchCache() {
    return Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats()
        .build();
  }
}
