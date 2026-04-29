package com.jianbo.crawler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClient 配置（Spring 6.1+ 推荐的 HTTP 客户端，替代 RestTemplate）
 *
 * 用途：
 *   1. 调用 local-ai-knowledge 服务 API（登录、上传文档）
 *   2. 调用公开 JSON 接口（知乎、B站热榜）
 */
@Slf4j
@Configuration
public class RestClientConfig {

    /**
     * 知识库服务专用 RestClient（预配置 baseUrl）
     */
    @Bean("knowledgeRestClient")
    public RestClient knowledgeRestClient(CrawlerProperties props) {
        String baseUrl = props.knowledgeApi().baseUrl();
        String apiKey = props.knowledgeApi().apiKey();

        // 使用 JDK 内置 HttpClient，天然支持虚拟线程
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(60));

        log.info("knowledgeRestClient 初始化完成：baseUrl={}, apiKey已配置={}", 
                baseUrl, apiKey != null && !apiKey.isBlank());

        var builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        
        // 默认携带 API Key 请求头
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-Crawler-Key", apiKey);
        }
        
        return builder.build();
    }

    /**
     * 通用 RestClient（无 baseUrl，用于调用各平台公开 API）
     */
    @Bean("commonRestClient")
    public RestClient commonRestClient() {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(15));

        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .build();
    }
}
