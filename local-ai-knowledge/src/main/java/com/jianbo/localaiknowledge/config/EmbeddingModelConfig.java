package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Embedding 模型配置
 *
 * <p>始终使用 Ollama embedding（本地 bge-m3，维度 1024）
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
    // RestClient 底层换成 JDK HttpClient，避开 Reactor Netty 传输；
    // 否则在 WebFlux 流式 Tool Calling 回调线程里会触发 block() 检测异常。
    RestClient.Builder restClientBuilder = RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory());
    OllamaApi api = new OllamaApi(ollamaBaseUrl, restClientBuilder, WebClient.builder());
    return OllamaEmbeddingModel.builder()
        .ollamaApi(api)
        .defaultOptions(OllamaOptions.builder().model(embeddingModel).build())
        .build();
  }
}
