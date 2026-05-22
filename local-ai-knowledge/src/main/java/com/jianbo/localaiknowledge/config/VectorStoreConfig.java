package com.jianbo.localaiknowledge.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Request;
import co.elastic.clients.transport.rest5_client.low_level.Response;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStoreOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

/**
 * 双 VectorStore 配置（ES + PG 共存）
 *
 * <p>ES = @Primary Bean 名 "esVectorStore"（检索优先走 ES）
 *
 * <p>PG = Spring AI 自动配置的 PgVectorStore，Bean 名 "vectorStore"
 *
 * <p>注入方式： @Autowired → ES（@Primary） @Autowired @Qualifier("vectorStore") VectorStore → PG
 *
 * <p>启动时若 ES 索引不存在，先用 IK 分词器创建，再让 Spring AI 跳过自动建表。
 */
@Slf4j
@Configuration
public class VectorStoreConfig {
  @Value("${spring.ai.vectorstore.elasticsearch.index-name:knowledge_vector_store}")
  private String esIndexName;

  @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1024}")
  private Integer esDimensions;

  /** ES VectorStore 设为 @Primary（检索优先） */
  @Bean
  @Primary
  @DependsOn("esIndexInitializer")
  public ElasticsearchVectorStore esVectorStore(
      Rest5Client restClient, EmbeddingModel embeddingModel) {

    ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
    options.setIndexName(esIndexName);
    options.setDimensions(esDimensions);
    return ElasticsearchVectorStore.builder(restClient, embeddingModel)
        .options(options)
        // 索引由 esIndexInitializer 用 IK mapping 预创建，这里不再让 Spring AI 自动建
        .initializeSchema(false)
            // 默认 TokenCountBatchingStrategy() 上限仅 8191 tokens，对中文场景每批只能容纳约 5-6 个 chunk，
            // 导致 vectorStore.add(200) 实际触发 ~37 次 embedding HTTP 调用。把上限调到 32K 后，
            // 单次 add(200) 大致只触发 ~6 次调用，整体 embedding 调用次数下降 6-8 倍。
            // 智谱 embedding-3 单 item 上限 8192 tokens，我们的 chunk 通常 <2000 tokens，不会超限。
            .batchingStrategy(new TokenCountBatchingStrategy(
                    com.knuddels.jtokkit.api.EncodingType.CL100K_BASE,
                    32000,   // 单次 embedding 请求 token 上限（合计）
                    0.1      // 预留 10% 余量，防 tokenizer 估计偏差
            ))
        .build();
  }

  /**
   * 启动时检查 ES 索引是否存在；不存在则用 IK 分词器（写入 ik_max_word，查询 ik_smart）创建。
   *
   * <p>已存在则保持不动，绝不覆盖用户的 mapping。
   */
  @Bean(name = "esIndexInitializer")
  public Object esIndexInitializer(Rest5Client restClient) throws Exception {
    Request head = new Request("HEAD", "/" + esIndexName);
    Response headResp = restClient.performRequest(head);
    int code = headResp.getStatusCode();
    if (code == 200) {
      log.info("ES 索引 {} 已存在，跳过初始化", esIndexName);
      return new Object();
    }
    log.info("ES 索引 {} 不存在，使用 IK 分词器创建...", esIndexName);

    String body =
        """
        {
          "settings": {
            "analysis": {
              "analyzer": {
                "ik_smart_combined": {
                  "type": "custom",
                  "tokenizer": "ik_max_word",
                  "filter": ["lowercase"]
                }
              }
            }
          },
          "mappings": {
            "properties": {
              "content": {
                "type": "text",
                "analyzer": "ik_max_word",
                "search_analyzer": "ik_smart"
              },
              "embedding": {
                "type": "dense_vector",
                "dims": %d,
                "index": true,
                "similarity": "cosine"
              },
              "metadata": {
                "type": "object",
                "properties": {
                  "source":      { "type": "keyword" },
                  "user_id":     { "type": "keyword" },
                  "doc_scope":   { "type": "keyword" },
                  "chunk_index": { "type": "keyword" },
                  "total_chunks":{ "type": "keyword" }
                }
              }
            }
          }
        }
        """
            .formatted(esDimensions);

    Request put = new Request("PUT", "/" + esIndexName);
    put.setJsonEntity(body);
    Response putResp = restClient.performRequest(put);
    log.info(
        "ES 索引 {} 创建完成 (status={})", esIndexName, putResp.getStatusCode());
    return new Object();
  }

  /**
   * ES 低级 REST 客户端（Spring AI 2.x 使用 elastic-java 9.x 的 Rest5Client，基于 Apache HttpClient 5） 读取 yaml
   * 中的 spring.elasticsearch.uris
   *
   * <p>连接池参数从默认的 maxConnPerRoute=10/maxConnTotal=30 提升到 20/50，
   * 以支撑并行入库时 4 线程 + embedding API 调用的并发连接需求。
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
    return Rest5Client.builder(httpHosts)
        .setConnectionManagerCallback(cmBuilder -> cmBuilder
            .setMaxConnPerRoute(20)
            .setMaxConnTotal(50))
        .setConnectionConfigCallback(connBuilder -> connBuilder
            .setConnectTimeout(Timeout.ofSeconds(5))
            .setSocketTimeout(Timeout.ofSeconds(120)))
        .build();
  }

  /**
   * 高级类型安全客户端（ES Java API Client，DSL 风格 builder + 编译期校验）。
   *
   * <p>与 {@link #elasticsearchRestClient} 共享同一份底层 HTTP 连接池，
   * 服务层（如 {@code EsKeywordSearchService}）优先用它替代手拼 JSON。
   */
  @Bean
  public ElasticsearchClient elasticsearchClient(Rest5Client restClient) {
    return new ElasticsearchClient(
        new Rest5ClientTransport(restClient, new JacksonJsonpMapper()));
  }
}
