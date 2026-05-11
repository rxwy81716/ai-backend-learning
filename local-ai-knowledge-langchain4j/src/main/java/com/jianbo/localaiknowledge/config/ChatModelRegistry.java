package com.jianbo.localaiknowledge.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.*;

/**
 * 多模型注册表（对标 Spring AI 版 ChatModelRegistry）。
 *
 * <h2>Spring AI vs LangChain4j 对比</h2>
 * <pre>
 * Spring AI:
 *   ChatModel chatModel;                    // 同步
 *   ChatClient.builder(chatModel).build();  // 流式 + 工具绑定
 *
 * LangChain4j:
 *   ChatModel chatModel;            // 同步
 *   StreamingChatModel streaming;   // 流式（独立接口！）
 * </pre>
 *
 * <p>LangChain4j 把同步和流式拆成两个独立接口，所以注册表要同时管理两套。</p>
 */
@Configuration
@Slf4j
public class ChatModelRegistry {

    /** 所有同步模型 */
    private final Map<String, ChatModel> chatModels = new LinkedHashMap<>();
    /** 所有流式模型 */
    private final Map<String, StreamingChatModel> streamingModels = new LinkedHashMap<>();

    // ===== 默认模型（由 Spring Boot Starter 自动注入） =====

    @Bean
    @Primary
    public ChatModel defaultChatModel(
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.base-url}") String baseUrl,
            @Value("${app.llm.model-name}") String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2048)
                .build();
    }

    @Bean
    @Primary
    public StreamingChatModel defaultStreamingModel(
            @Value("${app.llm.api-key}") String apiKey,
            @Value("${app.llm.base-url}") String baseUrl,
            @Value("${app.llm.model-name}") String modelName) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(0.7)
                .maxTokens(2048)
                .build();
    }

    @PostConstruct
    void init() {
        // 默认模型在 @Bean 中创建，这里注册额外模型
        // 从 app.models.* 配置中读取额外模型（如 GLM）
        log.info("ChatModelRegistry 初始化完成");
    }

    /** 注册额外模型 */
    public void register(String key, String apiKey, String baseUrl, String modelName) {
        ChatModel sync = OpenAiChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName(modelName)
                .temperature(0.7).maxTokens(2048).build();
        StreamingChatModel stream = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey).baseUrl(baseUrl).modelName(modelName)
                .temperature(0.7).maxTokens(2048).build();
        chatModels.put(key, sync);
        streamingModels.put(key, stream);
        log.info("注册模型: {} → {}", key, modelName);
    }

    /** 获取同步模型（找不到返回默认） */
    public ChatModel getChatModel(String key) {
        return chatModels.getOrDefault(key, chatModels.get("default"));
    }

    /** 获取流式模型 */
    public StreamingChatModel getStreamingModel(String key) {
        return streamingModels.getOrDefault(key, streamingModels.get("default"));
    }

    /** 列出可用模型 */
    public Set<String> listModelKeys() {
        return chatModels.keySet();
    }
}
