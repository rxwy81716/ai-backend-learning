package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 配置。
 *
 * <p>chat 走 OpenAI 兼容协议；具体连接哪个云厂商由激活的 profile 决定（见 application-glm.yml /
 * application-deepseek.yml）。Spring AI 自动配置基于 {@code spring.ai.model.chat=openai} +
 * {@code spring.ai.openai.*} 创建 {@link org.springframework.ai.openai.OpenAiChatModel}，
 * 本类只把它包成 {@link ChatClient}。
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
