package com.jianbo.localaiknowledge.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 带 Caffeine 缓存的 {@link EmbeddingModel} 装饰器。
 *
 * <p>命中场景：
 *
 * <ul>
 *   <li>VectorStore#similaritySearch(query) 内部为 query 计算 embedding（每次问答 1 次）
 *   <li>{@link com.jianbo.localaiknowledge.service.EmbeddingService#embed(String)} 单条调用
 *   <li>PlannerAgent 多轮检索时同义子 query 重复 embed
 * </ul>
 *
 * <p>规则：
 *
 * <ul>
 *   <li>仅缓存 {@code request.instructions.size() == 1} 的单条调用；批量调用（文档 ingestion）直接透传
 *   <li>key 为单条 instruction 文本本身；value 为 {@code float[]}
 *   <li>命中时返回新构造的 {@link EmbeddingResponse}，不复用 metadata（保证调用方拿到的不是上一次的远程 traceId）
 * </ul>
 *
 * <p>容量 / TTL：
 *
 * <ul>
 *   <li>maxSize = 2000：bge-m3 单条 1024 * 4B ≈ 4KB，2000 条约 8MB，足够覆盖典型用户 query 池
 *   <li>expireAfterWrite = 10 min：与 ragSearchCache 对齐；新文档入库不会污染（embedding 与文档无关）
 * </ul>
 *
 * <p>本装饰器不实现 Spring AI 的 {@code dimensions()} —— 走默认实现（首次调用真模型探测一次，后续自动缓存）。
 */
@Slf4j
public class CachedEmbeddingModel implements EmbeddingModel {

  private final EmbeddingModel delegate;
  private final Cache<String, float[]> cache;

  public CachedEmbeddingModel(EmbeddingModel delegate, int maxSize, long ttlMinutes) {
    this.delegate = delegate;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
            .recordStats()
            .build();
    log.info("✅ CachedEmbeddingModel 启用 | maxSize={}, ttl={}min", maxSize, ttlMinutes);
  }

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<String> instructions = request.getInstructions();

    // 批量调用直接透传：文档入库 chunk 重复率低、批量 API 本身已合并
    if (instructions == null || instructions.size() != 1) {
      return delegate.call(request);
    }

    String text = instructions.get(0);
    float[] cached = cache.getIfPresent(text);
    if (cached != null) {
      log.debug("[EmbedCache HIT] textLen={}", text.length());
      // 复制一份，避免下游修改污染缓存（Spring AI 个别实现会 in-place 归一化）
      float[] copy = cached.clone();
      return new EmbeddingResponse(
          List.of(new Embedding(copy, 0)), new EmbeddingResponseMetadata());
    }

    log.debug("[EmbedCache MISS] textLen={}", text.length());
    EmbeddingResponse response = delegate.call(request);
    if (response != null && response.getResult() != null) {
      float[] vec = response.getResult().getOutput();
      if (vec != null && vec.length > 0) {
        cache.put(text, vec.clone());
      }
    }
    return response;
  }

  /** Spring AI 2.0.0-M4 中 {@code embed(Document)} 是 abstract 方法，必须实现 */
  @Override
  public float[] embed(org.springframework.ai.document.Document document) {
    // 文档 ingestion 路径（每个 chunk 一次）：透传不缓存（chunk 文本极少重复）
    return delegate.embed(document);
  }

  // 其余 embed(String) / embed(List<String>) / dimensions() 是接口 default 方法，
  // 最终汇聚到 call(EmbeddingRequest)，自动受益于上面 call(...) 中的缓存逻辑。

  /** 暴露给 actuator / 自定义指标端点查看命中率 */
  public CacheStats stats() {
    return cache.stats();
  }

  /** 文档 / Embedding 模型版本切换时主动清空，防止维度不一致 */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  /** 上线后人工 review 命中率是否合理时调用 */
  public void logStats() {
    CacheStats s = cache.stats();
    log.info(
        "[EmbedCache stats] req={} hit={} hitRate={}% load={} evict={}",
        s.requestCount(),
        s.hitCount(),
        String.format("%.2f", s.hitRate() * 100),
        s.loadCount(),
        s.evictionCount());
  }
}
