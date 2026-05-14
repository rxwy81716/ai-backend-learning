package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.config.ChatModelProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 统一封装「OpenAI 兼容 Chat」构建逻辑，供 {@link ChatModelRegistry}（YAML 多模型）与
 * {@link com.jianbo.localaiknowledge.service.UserChatModelService}（用户库表配置）复用。
 *
 * <p>使用 {@link JdkClientHttpRequestFactory}，与注册表一致，避免在部分 WebFlux 回调线程上触发 Netty block 检测问题。
 */
@Component
public class OpenAiCompatibleChatModelFactory {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

  private static JdkClientHttpRequestFactory createRequestFactory() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    factory.setReadTimeout(READ_TIMEOUT);
    return factory;
  }

  public ChatModel createChatModel(
      String baseUrl,
      String apiKey,
      String completionsPath,
      String model,
      Double temperature,
      Integer maxTokens) {
    if (baseUrl == null || baseUrl.isBlank()
        || apiKey == null || apiKey.isBlank()
        || model == null || model.isBlank()) {
      throw new IllegalArgumentException("baseUrl、apiKey、model 不能为空");
    }
    RestClient.Builder rc = RestClient.builder().requestFactory(createRequestFactory());
    OpenAiApi.Builder apiBuilder =
        OpenAiApi.builder().baseUrl(baseUrl.trim()).apiKey(apiKey.trim()).restClientBuilder(rc);
    if (completionsPath != null && !completionsPath.isBlank()) {
      apiBuilder.completionsPath(completionsPath.trim());
    }
    OpenAiApi api = apiBuilder.build();
    double temp = temperature != null ? temperature : 0.3;
    int max = maxTokens != null ? maxTokens : 2048;
    OpenAiChatOptions options =
        OpenAiChatOptions.builder().model(model.trim()).temperature(temp).maxTokens(max).build();
    return OpenAiChatModel.builder().openAiApi(api).defaultOptions(options).build();
  }

  /** 从 YAML {@link ChatModelProperties.Provider} 构建 */
  public ChatModel createFromProvider(ChatModelProperties.Provider p) {
    return createChatModel(
        p.getBaseUrl(),
        p.getApiKey(),
        p.getCompletionsPath(),
        p.getModel(),
        p.getTemperature(),
        p.getMaxTokens());
  }

  public ChatClient createChatClient(
      String baseUrl,
      String apiKey,
      String completionsPath,
      String model,
      Double temperature,
      Integer maxTokens) {
    return ChatClient.create(createChatModel(baseUrl, apiKey, completionsPath, model, temperature, maxTokens));
  }
}
