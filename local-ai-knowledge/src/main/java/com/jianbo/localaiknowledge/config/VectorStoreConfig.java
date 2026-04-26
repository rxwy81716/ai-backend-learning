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
 * Elasticsearch VectorStore 配置（本地 Ollama bge-m3 embedding + ES）
 */
@Configuration
public class VectorStoreConfig {
  @Value("${spring.ai.vectorstore.elasticsearch.index-name:knowledge_vector_store}")
  private String esIndexName;

  @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1024}")
  private Integer esDimensions;

  /**
   * ES VectorStore — 唯一的向量库，设为 @Primary
   */
  @Bean
  @Primary
  public ElasticsearchVectorStore esVectorStore(
          RestClient restClient,
          EmbeddingModel embeddingModel) {

    ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
    options.setIndexName(esIndexName);
    options.setDimensions(esDimensions);
    // options.setSimilarity(SimilarityFunction.cosine);  // 默认就是 cosine
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