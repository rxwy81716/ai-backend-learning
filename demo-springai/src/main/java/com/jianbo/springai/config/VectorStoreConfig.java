package com.jianbo.springai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.RestClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 双 VectorStore 配置（PG + ES 共存）
 *
 * <p>原理： PG 的自动配置正常生效，注册 Bean 名 "vectorStore"（PgVectorStore） ES 的自动配置被排除（因为它和 PG 的 Bean 名冲突） 这里手动创建
 * ES Bean，名为 "esVectorStore"
 *
 * <p>注入方式： @Autowired VectorStore store; --> PG（@Primary） @Autowired @Qualifier("esVectorStore")
 * VectorStore --> ES
 */
@Configuration
public class VectorStoreConfig {
  @Value("${spring.ai.vectorstore.elasticsearch.index-name:vector_store_index}")
  private String esIndexName;

  @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1536}")
  private Integer esDimensions;

  /**
   * 注入时： @Autowired VectorStore store; --> 拿到
   * PG（@Primary） @Autowired @Qualifier("elasticsearchVectorStore") VectorStore es; --> 拿到 ES
   */
  /**
   * 把 PgVectorStore 包装为 @Primary
   *
   * <p>原理： pgvector starter 自动注册了一个 PgVectorStore 类型的 Bean 这里按类型注入它，再以 @Primary 重新暴露为 VectorStore
   * --> 所有不加 @Qualifier 的 VectorStore 注入都拿到 PG
   */
  @Bean
  @Primary
  public VectorStore primaryVectorStore(PgVectorStore pgVectorStore) {
    return pgVectorStore;
  }

  /**
   * ES VectorStore（手动创建，因为自动配置被排除了）
   *
   * Bean 名 = 方法名 = "esVectorStore"
   * 使用时：@Qualifier("esVectorStore")
   */
  @Bean
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
