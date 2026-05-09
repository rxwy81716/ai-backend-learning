package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.service.HybridSearchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 项目自定义 Micrometer 指标注册中心。
 *
 * <p>暴露给 {@code /actuator/prometheus} 的指标：
 *
 * <ul>
 *   <li><b>rag.embed.cache.{requests,hits,misses}</b>（gauge） —— 来自
 *       {@link CachedEmbeddingModel#stats()}，命中率 = hits / requests
 *   <li><b>rag.search.breaker.{state,total,rejected,failed}</b>（gauge） ——
 *       {@link com.jianbo.localaiknowledge.utils.SimpleCircuitBreaker} 状态
 *   <li><b>rag.chat.error.total{code="..."}</b>（counter） ——
 *       {@link com.jianbo.localaiknowledge.service.agent.StreamErrorHandler} 各错误码命中次数
 *   <li><b>rag.chat.duration{model="..."}</b>（timer） —— 单次 chat 耗时（按 modelKey 维度）
 * </ul>
 *
 * <p>Round 4 新增。无 Prometheus 时也不影响主流程：MeterRegistry 在 actuator 缺失时为
 * SimpleMeterRegistry，所有 Counter/Timer 仅记录在内存中不外发。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RagMetrics {

  private final MeterRegistry registry;

  /** {@link com.jianbo.localaiknowledge.service.agent.StreamErrorHandler} 错误计数缓存 */
  private final ConcurrentMap<String, Counter> errorCounters = new ConcurrentHashMap<>();
  /** Per-model chat 耗时计时器 */
  private final ConcurrentMap<String, Timer> chatTimers = new ConcurrentHashMap<>();

  /** 可选注入 —— 缺一个也能跑（启动顺序问题避免循环依赖时友好降级） */
  @Autowired(required = false)
  private EmbeddingModel embeddingModel;

  @Autowired(required = false)
  private HybridSearchService hybridSearchService;

  /** Spring 容器装好之后注册 gauge；用 lambda 引用拿最新值，不需要主动 push */
  @org.springframework.context.event.EventListener(
      org.springframework.context.event.ContextRefreshedEvent.class)
  public void registerGauges() {
    registerEmbedCacheGauges();
    registerSearchBreakerGauges();
    log.info("✅ RagMetrics 已注册 gauge：embed-cache + search-breaker");
  }

  private void registerEmbedCacheGauges() {
    if (!(embeddingModel instanceof CachedEmbeddingModel cem)) {
      log.debug("EmbeddingModel 未启用缓存装饰器，跳过 embed-cache gauge");
      return;
    }
    registry.gauge("rag.embed.cache.requests", cem, c -> c.stats().requestCount());
    registry.gauge("rag.embed.cache.hits", cem, c -> c.stats().hitCount());
    registry.gauge("rag.embed.cache.misses", cem, c -> c.stats().missCount());
    registry.gauge("rag.embed.cache.evictions", cem, c -> c.stats().evictionCount());
    registry.gauge("rag.embed.cache.hit_rate", cem, c -> c.stats().hitRate());
  }

  private void registerSearchBreakerGauges() {
    if (hybridSearchService == null) return;
    var breaker = hybridSearchService.getSearchBreaker();
    registry.gauge("rag.search.breaker.total", breaker, b -> (double) b.getTotalCalls());
    registry.gauge("rag.search.breaker.rejected", breaker, b -> (double) b.getRejectedCalls());
    registry.gauge("rag.search.breaker.failed", breaker, b -> (double) b.getFailedCalls());
    // state 0=CLOSED 1=HALF_OPEN 2=OPEN，方便 Grafana 阶梯显示
    registry.gauge("rag.search.breaker.state", breaker, b -> switch (b.getState()) {
      case CLOSED -> 0d;
      case HALF_OPEN -> 1d;
      case OPEN -> 2d;
    });
  }

  /** 累加一次错误码命中（StreamErrorHandler 每次 classify 后调用） */
  public void incrementErrorCode(String code) {
    if (code == null || code.isBlank()) return;
    errorCounters.computeIfAbsent(
        code,
        c -> Counter.builder("rag.chat.error.total")
            .tags(Tags.of("code", c))
            .description("Chat SSE 流式错误命中次数（按错误码拆分）")
            .register(registry))
        .increment();
  }

  /** 记录一次 chat 完整耗时（model 标签 = ChatModelRegistry key 或 "default"） */
  public void recordChatDuration(String modelKey, long durationMs) {
    String key = (modelKey == null || modelKey.isBlank()) ? "default" : modelKey;
    chatTimers.computeIfAbsent(
        key,
        k -> Timer.builder("rag.chat.duration")
            .tags(Tags.of("model", k))
            .description("Chat 单次请求总耗时（含路由 + Agent + 流式收尾）")
            .publishPercentiles(0.5, 0.9, 0.99)
            .register(registry))
        .record(java.time.Duration.ofMillis(durationMs));
  }
}
