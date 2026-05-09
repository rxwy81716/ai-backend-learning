package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.config.ChatModelProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatModel 注册表（运行时多模型切换）。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>启动时根据 {@link ChatModelProperties#getProviders()} 为每个 provider 构建一个
 *       {@link OpenAiChatModel}（仅在 api-key 非空时才注册）
 *   <li>把 Spring AI 自动配置出来的 @Primary {@link ChatModel} 作为兜底注册为 {@code default} key
 *   <li>对外提供 {@link #getClient(String)}：传入 modelKey，返回对应的 {@link ChatClient}；
 *       未知 key 时 fallback 到 {@code default-key} 指定的实例，再次未命中则回退 @Primary bean
 * </ul>
 *
 * <p>与 profile 切换的区别：profile 只在启动时生效、同一进程只能连一家厂商；注册表允许单进程同时持有
 * 多个 ChatModel 实例，前端请求可携带 {@code model=glm|deepseek|...} 即时切换，便于做 A/B、灰度、容灾兜底。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatModelRegistry {

  public static final String DEFAULT_KEY = "default";

  private final ChatModelProperties properties;
  /** Spring AI 自动配置的 @Primary ChatModel（来自当前激活 profile） */
  private final ChatModel primaryChatModel;

  /** key → ChatModel */
  private final Map<String, ChatModel> modelMap = new ConcurrentHashMap<>();

  /** key → ChatClient（ChatClient 可复用，避免每次请求重建） */
  private final Map<String, ChatClient> clientMap = new ConcurrentHashMap<>();

  @PostConstruct
  void init() {
    // 1. 注册 @Primary 作为 default 兜底
    modelMap.put(DEFAULT_KEY, primaryChatModel);
    clientMap.put(DEFAULT_KEY, ChatClient.create(primaryChatModel));
    log.info("✅ [ChatModelRegistry] 注册默认模型: default → {}",
        primaryChatModel.getClass().getSimpleName());

    // 2. 按配置构建其余 provider
    Map<String, ChatModelProperties.Provider> providers = properties.getProviders();
    if (providers == null || providers.isEmpty()) {
      log.warn("[ChatModelRegistry] 未配置 app.chat-models.providers，仅有 default 可用");
      return;
    }
    for (Map.Entry<String, ChatModelProperties.Provider> e : providers.entrySet()) {
      String key = e.getKey();
      ChatModelProperties.Provider p = e.getValue();
      if (p.getApiKey() == null || p.getApiKey().isBlank()
          || p.getBaseUrl() == null || p.getBaseUrl().isBlank()
          || p.getModel() == null || p.getModel().isBlank()) {
        log.warn("[ChatModelRegistry] 跳过 provider [{}]：base-url/api-key/model 缺失", key);
        continue;
      }
      try {
        ChatModel m = buildOpenAiChatModel(p);
        modelMap.put(key, m);
        clientMap.put(key, ChatClient.create(m));
        log.info("✅ [ChatModelRegistry] 注册模型: {} → {} @ {}", key, p.getModel(), p.getBaseUrl());
      } catch (Exception ex) {
        log.error("[ChatModelRegistry] 构建 provider [{}] 失败: {}", key, ex.getMessage(), ex);
      }
    }

    // 3. 如果配置了 default-key 且存在，则把它别名到 DEFAULT_KEY
    String dk = properties.getDefaultKey();
    if (dk != null && !dk.isBlank() && modelMap.containsKey(dk) && !DEFAULT_KEY.equals(dk)) {
      // default 指向显式命名的那一个（而不是 auto-config primary）
      modelMap.put(DEFAULT_KEY, modelMap.get(dk));
      clientMap.put(DEFAULT_KEY, clientMap.get(dk));
      log.info("✅ [ChatModelRegistry] default-key → {}", dk);
    }

    log.info("✅ [ChatModelRegistry] 注册完成，可用模型: {}", modelMap.keySet());
  }

  /**
   * 取对应 key 的 ChatClient；未知 key 返回默认。
   */
  public ChatClient getClient(String modelKey) {
    if (modelKey != null && !modelKey.isBlank()) {
      ChatClient c = clientMap.get(modelKey);
      if (c != null) return c;
      log.warn("[ChatModelRegistry] 未知 modelKey={}，fallback → default", modelKey);
    }
    return clientMap.get(DEFAULT_KEY);
  }

  /** 取对应 key 的 ChatModel（供 PlannerAgent 这种需要直接 call model 的场景） */
  public ChatModel getModel(String modelKey) {
    if (modelKey != null && !modelKey.isBlank()) {
      ChatModel m = modelMap.get(modelKey);
      if (m != null) return m;
    }
    return modelMap.get(DEFAULT_KEY);
  }

  /**
   * 列出所有可用模型 key（供 /api/rag/models 查询）。
   *
   * <p>返回 {@link ConcurrentHashMap} keySet 的不可变视图：
   * 调用方只读访问，避免每次都把整张 map clone 一份再取 keySet 的浪费。
   */
  public Set<String> availableKeys() {
    return Collections.unmodifiableSet(modelMap.keySet());
  }

  // ==================== 内部：构建 OpenAI 兼容 ChatModel ====================

  private ChatModel buildOpenAiChatModel(ChatModelProperties.Provider p) {
    RestClient.Builder rc = RestClient.builder().requestFactory(new JdkClientHttpRequestFactory());

    OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
        .baseUrl(p.getBaseUrl())
        .apiKey(p.getApiKey())
        .restClientBuilder(rc);
    if (p.getCompletionsPath() != null && !p.getCompletionsPath().isBlank()) {
      apiBuilder.completionsPath(p.getCompletionsPath());
    }
    OpenAiApi api = apiBuilder.build();

    OpenAiChatOptions options = OpenAiChatOptions.builder()
        .model(p.getModel())
        .temperature(p.getTemperature() != null ? p.getTemperature() : 0.3)
        .maxTokens(p.getMaxTokens() != null ? p.getMaxTokens() : 2048)
        .build();

    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(options)
        .build();
  }
}
