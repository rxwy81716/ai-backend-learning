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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
   * 文档库概览缓存（DocumentOverviewAgent 用）。
   *
   * <p>userId → 概览正文。TTL 60s 兼顾"刚上传文档想立刻看到"和"反复问同一个用户的反复重算"的取舍。
   * 把原先 DocumentOverviewAgent 内嵌的 ConcurrentHashMap+CacheEntry 自实现 TTL 统一到 Caffeine。
   */
  @Bean
  public Cache<String, String> docOverviewCache() {
    return Caffeine.newBuilder()
        .maximumSize(200)
        .expireAfterWrite(60, TimeUnit.SECONDS)
        .recordStats()
        .build();
  }

  /**
   * RAG 检索结果缓存。
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

  /**
   * 混合检索专用线程池。
   *
   * <p>修复 P0 风险：原先用 {@code CompletableFuture.supplyAsync(...)} 默认走
   * {@link java.util.concurrent.ForkJoinPool#commonPool()}，但向量检索 / BM25 是阻塞 IO（单次最长 ~3s），
   * 一旦把 commonPool 占满，会同时拖累整个 JVM 的 parallelStream 等并行操作。
   *
   * <p>策略：核心 = max(8, CPU*2)，最大 = max(32, core*1.5)，bounded queue 200，CallerRunsPolicy 兜底反压
   * （让调用线程自己跑该任务，避免静默丢弃 + 自动给上游降速）。
   */
  @Bean(destroyMethod = "shutdown")
  public ExecutorService ragSearchExecutor() {
    int corePoolSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
    // maxPoolSize 必须 >= corePoolSize（ThreadPoolExecutor 构造函数硬约束），
    // 且至少 32 兼顾低核机器突发流量；高核机器按 1.5 倍弹性扩容。
    int maxPoolSize = Math.max(32, corePoolSize * 3 / 2);
    AtomicInteger seq = new AtomicInteger();
    return new ThreadPoolExecutor(
        corePoolSize,
        maxPoolSize,
        60L,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(200),
        r -> {
          Thread t = new Thread(r, "rag-search-" + seq.incrementAndGet());
          t.setDaemon(true);
          return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy());
  }
}
