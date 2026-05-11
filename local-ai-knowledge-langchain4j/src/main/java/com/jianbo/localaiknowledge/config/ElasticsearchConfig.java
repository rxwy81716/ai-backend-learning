package com.jianbo.localaiknowledge.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch Java Client 配置（BM25 关键词检索用）。
 * langchain4j-elasticsearch 自带 EmbeddingStore，但不暴露低级 Client。
 * BM25 需要直接用 ES Java Client 的 search DSL。
 */
@Configuration
@Slf4j
public class ElasticsearchConfig {

    @Value("${app.es.uris:http://localhost:9200}")
    private String esUris;

    @Value("${app.es.username:}")
    private String username;

    @Value("${app.es.password:}")
    private String password;

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        HttpHost host = HttpHost.create(esUris);

        var builder = RestClient.builder(host);
        if (username != null && !username.isBlank()) {
            BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
            credsProv.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));
            builder.setHttpClientConfigCallback(hc -> hc.setDefaultCredentialsProvider(credsProv));
        }

        RestClient restClient = builder.build();
        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        log.info("ElasticsearchClient 初始化: {}", esUris);
        return new ElasticsearchClient(transport);
    }
}
