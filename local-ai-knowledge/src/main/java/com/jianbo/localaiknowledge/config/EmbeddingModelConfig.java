package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Embedding 模型配置
 *
 * <p>始终使用 Ollama embedding（本地 bge-m3，维度 1024）
 *
 * <p>Chat 模型由 Spring AI 自动配置（MiniMax 或 Ollama）
 */
@Slf4j
@Configuration
public class EmbeddingModelConfig {

  @Value("${spring.ai.ollama.base-url:http://10.56.60.249:11434}")
  private String ollamaBaseUrl;

  @Value("${spring.ai.ollama.embedding.options.model:bge-m3}")
  private String embeddingModel;

  @Bean
  @Primary
  public EmbeddingModel customEmbeddingModel() {
    log.info("✅ Embedding 提供者: Ollama ({})", embeddingModel);
    // RestClient 底层换成 JDK HttpClient，避开 Reactor Netty；
    // 否则在 WebFlux 流式 Tool Calling 回调线程里同步发请求会触发 block() 检测异常。
    // Embedding 调用全部走同步 RestClient，无需自定义 WebClient（builder 不传即用 SDK 默认值）。
    RestClient.Builder restClientBuilder =
        RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());
    OllamaApi api =
        OllamaApi.builder().baseUrl(ollamaBaseUrl).restClientBuilder(restClientBuilder).build();
    // 关键：显式设置请求时使用的模型名，否则会 fallback 到 SDK 默认值（mxbai-embed-large/768维），
    // 与 ES 索引配置的 1024 维（bge-m3）不匹配会导致写入/检索失败。
    // pullModelStrategy 默认 WHEN_MISSING：首次请求时会自动拉模型，无需 additionalModels 预拉。
    return OllamaEmbeddingModel.builder()
        .ollamaApi(api)
        .defaultOptions(OllamaEmbeddingOptions.builder().model(embeddingModel).build())
        .build();
  }
}
