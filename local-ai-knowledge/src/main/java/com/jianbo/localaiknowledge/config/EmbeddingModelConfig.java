package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Embedding 模型配置。
 *
 * <p>固定使用硅基流动 SiliconFlow 的 BAAI/bge-m3（1024 维，OpenAI 兼容协议）。
 *
 * <p>为什么自己 new 而不是用 Spring AI 的 OpenAI 自动配置：
 *
 * <ul>
 *   <li>chat 也走 OpenAI 协议（按 profile 切到 GLM / DeepSeek），两边的 base-url / api-key / model 不同，
 *       无法共用同一份 {@code spring.ai.openai.*} 配置；
 *   <li>RestClient 底层显式换成 JDK HttpClient，避开 Reactor Netty 在 WebFlux 流式 Tool Calling
 *       回调线程上的 block() 检测异常。
 * </ul>
 */
@Slf4j
@Configuration
public class EmbeddingModelConfig {

  @Value("${app.embedding.siliconflow.base-url:https://api.siliconflow.cn}")
  private String baseUrl;

  @Value("${app.embedding.siliconflow.api-key:${SILICONFLOW_API_KEY:}}")
  private String apiKey;

  @Value("${app.embedding.siliconflow.model:BAAI/bge-m3}")
  private String model;

  @Bean
  @Primary
  public EmbeddingModel customEmbeddingModel() {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException(
          "未配置 SiliconFlow API Key，请设置 app.embedding.siliconflow.api-key 或环境变量 SILICONFLOW_API_KEY");
    }
    log.info("✅ Embedding 提供者: SiliconFlow ({}) @ {}", model, baseUrl);

    RestClient.Builder restClientBuilder =
        RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());

    OpenAiApi openAiApi =
        OpenAiApi.builder()
            .baseUrl(baseUrl)
            .apiKey(apiKey)
            .restClientBuilder(restClientBuilder)
            .build();

    return new OpenAiEmbeddingModel(
        openAiApi,
        MetadataMode.EMBED,
        OpenAiEmbeddingOptions.builder().model(model).build());
  }
}
