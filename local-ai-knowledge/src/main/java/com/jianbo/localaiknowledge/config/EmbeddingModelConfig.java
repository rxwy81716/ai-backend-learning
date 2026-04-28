package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.minimax.MiniMaxEmbeddingModel;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Embedding 模型配置（按 provider 条件加载）
 *
 * 公司（minimax）：使用独立 embedding-api-key 调用 MiniMax embo-01，维度 1536
 * 家里（ollama）：使用本地 Ollama bge-m3，维度 1024
 *
 * 注意：切换模型后向量维度不同，ES 索引需要重建！
 */
public class EmbeddingModelConfig {

    /** MiniMax Embedding（公司环境，默认） */
    @Configuration
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "minimax", matchIfMissing = true)
    @Slf4j
    static class MiniMaxEmbeddingConfig {

        @Value("${app.minimax.embedding-api-key:}")
        private String minimaxApiKey;

        @Bean
        @Primary
        public EmbeddingModel customEmbeddingModel() {
            log.info("✅ Embedding 提供者: MiniMax (embo-01)");
            MiniMaxApi miniMaxApi = new MiniMaxApi(minimaxApiKey);
            return new MiniMaxEmbeddingModel(miniMaxApi);
        }
    }

    /** Ollama Embedding（家里环境） */
    @Configuration
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    @Slf4j
    static class OllamaEmbeddingConfig {

        @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
        private String ollamaBaseUrl;

        @Value("${spring.ai.ollama.embedding.options.model:bge-m3}")
        private String embeddingModel;

        @Bean
        @Primary
        public EmbeddingModel customEmbeddingModel() {
            log.info("✅ Embedding 提供者: Ollama ({})", embeddingModel);
            OllamaApi api = new OllamaApi(ollamaBaseUrl);
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(api)
                    .defaultOptions(OllamaOptions.builder().model(embeddingModel).build())
                    .build();
        }
    }
}
