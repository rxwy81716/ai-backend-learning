package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置。
 *
 * <p>实际使用哪个 LLM 由 {@code spring.ai.model.chat} 选择器决定（{@code minimax} / {@code ollama} / ...），
 * Spring AI starter 自动只创建对应的 {@link ChatModel}，本类只把它包成 {@link ChatClient}。
 */
@Configuration
@Slf4j
public class ChatClientConfig {

  @Bean
  public ChatClient ragChatClient(ChatModel chatModel) {
    log.info("✅ ChatClient 使用 ChatModel: {}", chatModel.getClass().getSimpleName());
    return ChatClient.create(chatModel);
  }
}
