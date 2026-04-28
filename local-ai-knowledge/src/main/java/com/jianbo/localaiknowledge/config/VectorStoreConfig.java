package com.jianbo.localaiknowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 双 VectorStore 配置（ES + PG 共存）
 *
 * <p>ES = @Primary Bean 名 "esVectorStore"（检索优先走 ES）
 * <p>PG = Spring AI 自动配置的 PgVectorStore，Bean 名 "vectorStore"
 *
 * <p>注入方式：
 *   @Autowired                                          → ES（@Primary）
 *   @Autowired @Qualifier("vectorStore") VectorStore    → PG
 */
@Configuration
public class VectorStoreConfig {
  @Value("${spring.ai.vectorstore.elasticsearch.index-name:knowledge_vector_store}")
  private String esIndexName;

  @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1536}")
  private Integer esDimensions;

  /**
   * ES VectorStore 设为 @Primary（检索优先）
   */
  @Bean
  @Primary
  public ElasticsearchVectorStore esVectorStore(
          RestClient restClient,
          EmbeddingModel embeddingModel) {

    ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
    options.setIndexName(esIndexName);
    options.setDimensions(esDimensions);
    return ElasticsearchVectorStore.builder(restClient, embeddingModel)
            .options(options)
            .initializeSchema(true)
            .batchingStrategy(new TokenCountBatchingStrategy())
            .build();
  }

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  /**
   * ES 低级 REST 客户端（Spring Boot 4.x 不再自动配置）
   * 读取 yaml 中的 spring.elasticsearch.uris
   */
  @Bean
  public RestClient elasticsearchRestClient(
          @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
    String[] hosts = uris.split(",");
    org.apache.http.HttpHost[] httpHosts = new org.apache.http.HttpHost[hosts.length];
    for (int i = 0; i < hosts.length; i++) {
      java.net.URI uri = java.net.URI.create(hosts[i].trim());
      httpHosts[i] = new org.apache.http.HttpHost(
              uri.getHost(),
              uri.getPort(),
              uri.getScheme()
      );
    }
    return RestClient.builder(httpHosts).build();
  }
}
