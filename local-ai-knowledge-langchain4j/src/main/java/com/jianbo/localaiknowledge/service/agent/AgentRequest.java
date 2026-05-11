package com.jianbo.localaiknowledge.service.agent;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Agent 请求上下文（对标 Spring AI 版）。
 *
 * <h2>消息类型对比</h2>
 * <pre>
 * Spring AI:                       LangChain4j:
 *   org.springframework.ai          dev.langchain4j.data.message
 *   .chat.messages.Message          .ChatMessage
 *   .SystemMessage                  .SystemMessage
 *   .UserMessage                    .UserMessage
 *   .AssistantMessage               .AiMessage
 * </pre>
 *
 * <p>注意：Spring AI 叫 AssistantMessage，LangChain4j 叫 AiMessage。</p>
 */
public record AgentRequest(
        String sessionId,
        String question,
        String userId,
        List<ChatMessage> messages,
        RagToolContext toolContext,
        boolean thinking
) {}
