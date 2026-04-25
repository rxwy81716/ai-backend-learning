package com.jianbo.springai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class EmbeddingModelConfig {
  @Value("${app.minimax.embedding-api-key:}")
  private String minimaxApiKey;

  /** MiniMax Embedding模型 特点：中文能力强，企业级服务 */
  @Bean
  @Primary // 设置默认模型，防止 Autowired 时冲突
  public EmbeddingModel customEmbeddingModel() {
    MiniMaxApi miniMaxApi = new MiniMaxApi(minimaxApiKey);
    return new MiniMaxEmbeddingModel(miniMaxApi);
  }
}
