package com.jianbo.localaiknowledge.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 向量存储配置（对标 Spring AI 版 VectorStoreConfig）。
 *
 * <h2>API 对比</h2>
 * <pre>
 * Spring AI:
 *   VectorStore vectorStore;
 *   vectorStore.add(List&lt;Document&gt;);
 *   vectorStore.similaritySearch(SearchRequest);
 *
 * LangChain4j:
 *   EmbeddingStore&lt;TextSegment&gt; embeddingStore;
 *   embeddingStore.add(embedding, textSegment);
 *   embeddingStore.search(EmbeddingSearchRequest);
 * </pre>
 *
 * <p>核心区别：Spring AI 的 VectorStore 内部自动调 EmbeddingModel 做向量化，
 * LangChain4j 的 EmbeddingStore 是纯存储层，需要外部先调 EmbeddingModel 再存入。</p>
 */
@Configuration
@Slf4j
public class VectorStoreConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${app.embedding.api-key}") String apiKey,
            @Value("${app.embedding.base-url}") String baseUrl,
            @Value("${app.embedding.model-name}") String modelName,
            @Value("${app.embedding.dimensions:1024}") int dimensions) {
        log.info("初始化 EmbeddingModel: {} (dim={})", modelName, dimensions);
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .dimensions(dimensions)
                .build();
    }

    @Bean
    public ElasticsearchEmbeddingStore embeddingStore(
            @Value("${app.es.uris}") String esUris,
            @Value("${app.es.index-name}") String indexName,
            @Value("${app.es.username:}") String username,
            @Value("${app.es.password:}") String password) {
        log.info("初始化 ElasticsearchEmbeddingStore: {} → {}", esUris, indexName);

        var builder = ElasticsearchEmbeddingStore.builder()
                .serverUrl(esUris)
                .indexName(indexName);

        if (username != null && !username.isBlank()) {
            builder.userName(username).password(password);
        }

        return builder.build();
    }
}
