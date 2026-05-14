package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Embedding 模型配置。
 *
 * <p>默认走智谱 {@code embedding-3}（OpenAI 兼容 {@code /v4/embeddings}），向量维度由
 * {@code app.embedding.zhipu.dimensions} 指定，须与 {@code spring.ai.vectorstore.*.dimensions} 一致。
 *
 * <p>为什么自己 new 而不是用 Spring AI 的 OpenAI 自动配置：
 *
 * <ul>
 *   <li>chat 也走 OpenAI 协议（按 profile 切到 GLM / DeepSeek），两边的 base-url / api-key / model 不同，
 *       无法共用同一份 {@code spring.ai.openai.*} 配置；
 *   <li>RestClient 底层显式换成 JDK HttpClient，避开 Reactor Netty 在 WebFlux 流式 Tool Calling
 *       回调线程上的 block() 检测异常。
 * </ul>
 */
@Slf4j
@Configuration
public class EmbeddingModelConfig {

  @Value("${app.embedding.zhipu.base-url:https://open.bigmodel.cn/api/paas}")
  private String baseUrl;

  @Value("${app.embedding.zhipu.api-key:}")
  private String apiKey;

  @Value("${app.embedding.zhipu.model:embedding-3}")
  private String model;

  @Value("${app.embedding.zhipu.embeddings-path:/v4/embeddings}")
  private String embeddingsPath;

  /** 智谱 embedding-3 支持 256~2048；须与向量库 mapping 维度一致 */
  @Value("${app.embedding.zhipu.dimensions:1024}")
  private int dimensions;

  @Value("${app.embedding.cache.enabled:true}")
  private boolean cacheEnabled;

  @Value("${app.embedding.cache.max-size:2000}")
  private int cacheMaxSize;

  @Value("${app.embedding.cache.ttl-minutes:10}")
  private long cacheTtlMinutes;

  @Bean
  @Primary
  public EmbeddingModel customEmbeddingModel() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "未配置智谱 Embedding API Key，请设置 app.embedding.zhipu.api-key 或环境变量 ZHIPU_API_KEY");
    }
    log.info("✅ Embedding 提供者: 智谱 {} ({} 维) @ {}{}", model, dimensions, baseUrl, embeddingsPath);

    RestClient.Builder restClientBuilder =
        RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());

    OpenAiApi openAiApi =
        OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .embeddingsPath(embeddingsPath)
            .restClientBuilder(restClientBuilder)
            .build();

    EmbeddingModel raw =
        new OpenAiEmbeddingModel(
            openAiApi,
            MetadataMode.EMBED,
            OpenAiEmbeddingOptions.builder().model(model).dimensions(dimensions).build());

    if (!cacheEnabled) {
      log.info("⚠ EmbeddingModel 缓存已禁用（app.embedding.cache.enabled=false）");
      return raw;
    }
    return new CachedEmbeddingModel(raw, cacheMaxSize, cacheTtlMinutes);
  }
}
