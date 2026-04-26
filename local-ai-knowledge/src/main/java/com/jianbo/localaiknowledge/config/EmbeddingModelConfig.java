package com.jianbo.localaiknowledge.config;

import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingModelConfig {

  @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
  private String ollamaBaseUrl;

  @Value("${spring.ai.ollama.embedding.options.model:bge-m3}")
  private String embeddingModel;

  /** 本地 Ollama bge-m3 Embedding 模型 */
  @Bean
  @Primary
  public OllamaEmbeddingModel ollamaEmbeddingModel() {
    OllamaApi api = new OllamaApi(ollamaBaseUrl);
    return OllamaEmbeddingModel.builder()
            .ollamaApi(api)
            .defaultOptions(OllamaOptions.builder().model(embeddingModel).build())
            .build();
  }
}
