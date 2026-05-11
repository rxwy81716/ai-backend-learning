package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息构建器（对标 Spring AI 版 ChatMessageBuilder）。
 *
 * <h2>消息类型映射</h2>
 * <pre>
 * Spring AI                          LangChain4j
 * ──────────────────────────────────────────────
 * SystemMessage(content)             SystemMessage.from(content)
 * UserMessage(content)               UserMessage.from(content)
 * AssistantMessage(content)          AiMessage.from(content)
 * </pre>
 *
 * <p>LangChain4j 叫 {@code AiMessage} 而不是 {@code AssistantMessage}，
 * 这是两个框架最容易搞混的地方。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatMessageBuilder {

    private final ChatHistoryCacheService historyService;
    private final SystemPromptService systemPromptService;
    private final ChatContextUtil chatContextUtil;

    /**
     * 构建完整消息序列：[SystemMessage, 历史消息..., 当前UserMessage]
     */
    public List<ChatMessage> build(String sessionId, String userId, String question,
                                   SpecializedAgent agent, String promptName, String chatMode) {
        List<ChatMessage> messages = new ArrayList<>();

        // 1. System Prompt
        String sysPrompt = resolveAgentSystemPrompt(promptName, agent);
        messages.add(SystemMessage.from(sysPrompt));

        // 2. 历史消息
        List<com.jianbo.localaiknowledge.model.ChatMessage> history =
                historyService.loadHistory(sessionId);
        if (history != null) {
            for (var msg : history) {
                if ("user".equals(msg.getRole())) {
                    messages.add(UserMessage.from(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(AiMessage.from(msg.getContent()));
                }
            }
        }

        // 3. 当前问题
        messages.add(UserMessage.from(question));

        // 4. Token 裁剪
        chatContextUtil.trimByToken(messages);

        return messages;
    }

    public String resolveAgentSystemPrompt(String promptName, SpecializedAgent agent) {
        SystemPrompt prompt = null;
        if (promptName != null && !promptName.isBlank()) {
            prompt = systemPromptService.getByName(promptName);
        }
        if (prompt == null) {
            try {
                prompt = systemPromptService.getDefault();
            } catch (Exception e) {
                log.debug("加载默认 SystemPrompt 失败，使用 Agent 内置提示: {}", e.getMessage());
            }
        }
        if (prompt == null || prompt.getContent() == null || prompt.getContent().isBlank()) {
            return agent.systemPrompt();
        }
        String userPart = prompt.getContent().replace("{context}", "").trim();
        return agent.systemPrompt() + "\n\n附加风格指令：\n" + userPart;
    }
}
