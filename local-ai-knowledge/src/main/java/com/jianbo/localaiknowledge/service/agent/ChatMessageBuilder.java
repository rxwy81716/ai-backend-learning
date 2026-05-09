package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.utils.ChatContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天上下文消息列表构造器。
 *
 * <p>从 {@code MultiAgentOrchestrator} 抽出（review #2 P1 架构）。负责：
 *
 * <ol>
 *   <li>注入 system prompt（Agent 内置 + 用户自定义合并）
 *   <li>从 {@link ChatHistoryCacheService} 拉历史，仅保留同 chatMode 的消息
 *   <li>追加当前用户问题
 *   <li>调用 {@link ChatContextUtil#trimByToken(List)} 做 token 上限裁剪
 * </ol>
 *
 * <p>纯组装类，无副作用；可独立单测。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatMessageBuilder {

  /** 单次请求最多保留的历史消息条数（裁剪前的硬上限） */
  public static final int MAX_HISTORY_MESSAGES = 20;

  private final ChatHistoryCacheService chatHistoryCache;
  private final ChatContextUtil chatContextUtil;
  private final SystemPromptService systemPromptService;
  private final FollowUpDetector followUpDetector;

  /**
   * 构造发给 LLM 的完整消息序列。
   *
   * @param sysPrompt 系统提示
   * @param sessionId 会话 ID（null 表示新会话，不拉历史）
   * @param question 当前用户问题
   * @param currentMode 当前 chatMode（KNOWLEDGE / LLM），用于历史过滤
   */
  public List<Message> build(String sysPrompt, String sessionId, String question, String currentMode) {
    List<Message> messages = new ArrayList<>();
    messages.add(new SystemMessage(sysPrompt));
    if (sessionId != null) {
      List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, MAX_HISTORY_MESSAGES);
      for (ChatMessage msg : history) {
        if (!followUpDetector.isSameMode(msg, currentMode)) continue;
        switch (msg.getRole()) {
          case "user" -> messages.add(new UserMessage(msg.getContent()));
          case "assistant" -> messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }
    messages.add(new UserMessage(question));
    chatContextUtil.trimByToken(messages);
    return messages;
  }

  /**
   * 拼接最终 system prompt = Agent 默认提示 + 用户自定义 prompt。
   *
   * <p>查找顺序：传入 promptName → 数据库默认 → Agent 内置兜底。
   */
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
