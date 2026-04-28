package com.jianbo.localaiknowledge.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多模型 ChatClient 配置
 *
 * 通过 app.llm.provider 配置切换：
 *   - minimax（默认）：公司使用，走 MiniMax 云端 API
 *   - ollama：家里使用，走本地 Ollama
 *
 * 下游代码统一注入 @Qualifier("MiniMaxChatClient")，
 * 实际使用哪个模型完全由配置决定，业务代码零改动。
 *
 * 注意：需要在 profile yml 中用 spring.autoconfigure.exclude 排除不用的 Starter，
 * 否则两个 Starter 都会尝试初始化连接。
 */
public class ChatClientConfig {

    /** MiniMax ChatClient（公司环境，默认） */
    @Configuration
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "minimax", matchIfMissing = true)
    @Slf4j
    static class MiniMaxConfig {
        @Bean("MiniMaxChatClient")
        public ChatClient miniMaxChatClient(org.springframework.ai.minimax.MiniMaxChatModel model) {
            log.info("✅ LLM 提供者: MiniMax（云端 API）");
            return ChatClient.create(model);
        }
    }

    /** Ollama ChatClient（家里环境） */
    @Configuration
    @ConditionalOnProperty(name = "app.llm.provider", havingValue = "ollama")
    @Slf4j
    static class OllamaConfig {
        @Bean("MiniMaxChatClient")
        public ChatClient ollamaChatClient(OllamaChatModel model) {
            log.info("✅ LLM 提供者: Ollama（本地模型）");
            return ChatClient.create(model);
        }
    }
}
