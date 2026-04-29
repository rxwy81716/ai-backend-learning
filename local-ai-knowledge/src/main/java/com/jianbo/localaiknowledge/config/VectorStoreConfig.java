package com.jianbo.localaiknowledge.config;

import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 双 VectorStore 配置（ES + PG 共存）
 *
 * <p>ES = @Primary Bean 名 "esVectorStore"（检索优先走 ES）
 *
 * <p>PG = Spring AI 自动配置的 PgVectorStore，Bean 名 "vectorStore"
 *
 * <p>注入方式： @Autowired → ES（@Primary） @Autowired @Qualifier("vectorStore") VectorStore → PG
 */
@Configuration
public class VectorStoreConfig {
  @Value("${spring.ai.vectorstore.elasticsearch.index-name:knowledge_vector_store}")
  private String esIndexName;

  @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1024}")
  private Integer esDimensions;

  /** ES VectorStore 设为 @Primary（检索优先） */
  @Bean
  @Primary
  public ElasticsearchVectorStore esVectorStore(
      Rest5Client restClient, EmbeddingModel embeddingModel) {

    ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
    options.setIndexName(esIndexName);
    options.setDimensions(esDimensions);
    return ElasticsearchVectorStore.builder(restClient, embeddingModel)
        .options(options)
        .initializeSchema(true)
        .batchingStrategy(new TokenCountBatchingStrategy())
        .build();
  }

  /**
   * ES 低级 REST 客户端（Spring AI 2.x 使用 elastic-java 9.x 的 Rest5Client，基于 Apache HttpClient 5） 读取 yaml
   * 中的 spring.elasticsearch.uris
   */
  @Bean
  public Rest5Client elasticsearchRestClient(
      @Value("${spring.elasticsearch.uris:http://localhost:9200}") String uris) {
    String[] hosts = uris.split(",");
    HttpHost[] httpHosts = new HttpHost[hosts.length];
    for (int i = 0; i < hosts.length; i++) {
      java.net.URI uri = java.net.URI.create(hosts[i].trim());
      httpHosts[i] = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    }
    return Rest5Client.builder(httpHosts).build();
  }
}
