package com.jianbo.localaiknowledge.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 追问检测 + 跨模式历史隔离工具。
 *
 * <p>从 {@code MultiAgentOrchestrator} 中抽出（review #2 P1 架构）。原因：
 *
 * <ul>
 *   <li>Orchestrator 已 600+ 行，"短追问命中 → 上文注入"是一条独立的语义识别策略，应单独可测
 *   <li>{@link #isSameMode(ChatMessage, String)} 同时被 ChatMessageBuilder 与本类使用，
 *       下沉到此处避免循环引用
 * </ul>
 *
 * <p>判定规则：
 *
 * <ul>
 *   <li>问题长度 ≤ {@value #FOLLOW_UP_MAX_LENGTH} 字符（长问题视为独立 query）
 *   <li>命中 {@link #FOLLOW_UP_PATTERN}（序数指代 / 追问动作词）
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FollowUpDetector {

  /** 追问问题最大长度（超过即视为独立新问题） */
  public static final int FOLLOW_UP_MAX_LENGTH = 30;

  /** META 中存放对话模式（KNOWLEDGE / LLM）的 key */
  public static final String META_KEY_MODE = "chatMode";
  public static final String MODE_KNOWLEDGE = "KNOWLEDGE";

  /**
   * 追问关键词正则：序数引用 / 历史指代 / 追问动作 / 文档指代 / 总结类。
   * 内容与原 {@code MultiAgentOrchestrator.FOLLOW_UP_PATTERN} 保持一致，移过来后保证行为不变。
   */
  public static final Pattern FOLLOW_UP_PATTERN = Pattern.compile(
      "第[一二三四五六七八九十\\d]+[个篇份条段章]|"
          + "刚才|刚刚|前面|上面|之前|上次|上一个|"
          + "详细说说|展开讲讲|具体说说|再详细|多说点|"
          + "继续说|接着说|然后呢|还有呢|"
          + "这个文档|那个文档|这篇|那篇|"
          + "总结一下|概括一下|归纳一下",
      Pattern.UNICODE_CASE);

  private final ChatHistoryCacheService chatHistoryCache;
  private final ObjectMapper objectMapper;

  /** 短问题 + 命中追问关键字：判定为追问 */
  public boolean isFollowUp(String question) {
    if (question == null || question.isBlank()) return false;
    if (question.length() > FOLLOW_UP_MAX_LENGTH) return false;
    return FOLLOW_UP_PATTERN.matcher(question).find();
  }

  /**
   * 加载会话最近一条 assistant 消息内容（同模式），用于追问命中后注入上下文。
   *
   * @param sessionId 会话 ID
   * @param mode 当前 chatMode（KNOWLEDGE / LLM），用于跨模式隔离
   * @return 最近一条 assistant 内容（截前 2000 字符）；无则返回 null
   */
  public String loadLastAssistantContent(String sessionId, String mode) {
    if (sessionId == null) return null;
    List<ChatMessage> history = chatHistoryCache.loadRecentHistory(sessionId, 5);
    for (int i = history.size() - 1; i >= 0; i--) {
      ChatMessage msg = history.get(i);
      if ("assistant".equals(msg.getRole()) && isSameMode(msg, mode)) {
        String content = msg.getContent();
        if (content != null && !content.isBlank()) {
          return content.length() > 2000 ? content.substring(0, 2000) : content;
        }
      }
    }
    return null;
  }

  /**
   * 判断历史消息与当前模式是否一致。模式不同时不应混用历史
   * （比如 LLM 直答的回复不该污染知识库回答的上下文）。
   */
  public boolean isSameMode(ChatMessage msg, String currentMode) {
    String stored = MODE_KNOWLEDGE;
    String meta = msg.getMetadata();
    if (meta != null && !meta.isBlank()) {
      try {
        Map<?, ?> map = objectMapper.readValue(meta, Map.class);
        Object v = map.get(META_KEY_MODE);
        if (v != null) stored = v.toString();
      } catch (Exception ignore) {
        // 解析失败按默认 KNOWLEDGE 处理
      }
    }
    return currentMode.equalsIgnoreCase(stored);
  }
}
