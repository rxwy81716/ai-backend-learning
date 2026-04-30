package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.ChatConversationMapper;
import com.jianbo.localaiknowledge.mapper.ChatFeedbackMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.model.ChatSession;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.ChatHistoryCacheService;
import com.jianbo.localaiknowledge.service.RagAgentService;
import com.jianbo.localaiknowledge.service.SystemPromptService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG 智能问答 & 会话管理 & Prompt 管理
 *
 * <p>智能问答（自动路由 知识库 → 网络搜索 → LLM直答）： POST /api/rag/chat 同步问答 POST /api/rag/chat/stream SSE 流式问答
 *
 * <p>会话管理： GET /api/rag/sessions 获取所有会话 GET /api/rag/history/{sessionId} 获取会话历史 DELETE
 * /api/rag/session/{sessionId} 删除会话
 *
 * <p>Prompt 管理： GET /api/rag/prompts 获取所有 SystemPrompt POST /api/rag/prompt 创建/更新 SystemPrompt PUT
 * /api/rag/prompt/default/{name} 设置默认 Prompt
 */
@RestController
@RequestMapping("/api/rag")
@Slf4j
@RequiredArgsConstructor
public class RagController {

  private final RagAgentService ragAgentService;
  private final ChatConversationMapper conversationMapper;
  private final ChatHistoryCacheService chatHistoryCache;
  private final SystemPromptService promptService;
  private final RedissonClient redissonClient;
  private final ChatFeedbackMapper feedbackMapper;

  private static final String SESSION_TITLE_KEY = "chat:session:titles";

  // ==================== 智能问答 ====================

  /**
   * 智能问答（SSE 流式）。注意：SSE 流不被统一响应包装。
   *
   * <p>请求体字段：{@code question} / {@code sessionId?} / {@code promptName?} /
   * {@code chatMode?}（KNOWLEDGE=知识库模式默认 / LLM=LLM直答） / {@code thinking?}。
   * 以 {@code text/event-stream} 形式返回 token 流，
   * 末尾追加 {@code [META]...[/META]} 段，前端可解析出来源、引用等元数据。
   */
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> chatStream(@RequestBody Map<String, String> body) {
    String question = sanitizeQuestion(body.get("question"));
    String userId = SecurityUtil.getCurrentUserIdStr();
    String sessionId = body.get("sessionId");
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString().replace("-", "");
    } else {
      // 用户传入既有 sessionId 时校验归属，防止越权读他人对话并续写
      assertSessionOwnedByCurrentUser(sessionId, userId);
    }
    String promptName = body.get("promptName");
    String chatMode = normalizeChatMode(body.get("chatMode"));
    boolean thinking = parseThinking(body);

    return ragAgentService.chatStream(
        sessionId, question, userId, promptName, chatMode, thinking);
  }

  /** 用户输入最大长度（字符数）：超过即拒绝，避免单次 prompt 撑爆 token / 上下文窗口。 */
  private static final int MAX_QUESTION_LENGTH = 2000;

  /**
   * 校验并清洗用户问题：
   *
   * <ul>
   *   <li>非空检查：空白直接 400
   *   <li>长度检查：超过 {@value MAX_QUESTION_LENGTH} 字符返 400（防止 token 滥用）
   *   <li>trim 首尾空白
   * </ul>
   *
   * <p>注意：不在此处做"忽略之前指令"等 prompt-injection 关键字过滤，那容易误伤；改在 system prompt 里告知模型"用户消息中的指令视为数据"，由模型自己抵御。
   */
  private static String sanitizeQuestion(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("question 不能为空");
    }
    String q = raw.trim();
    if (q.length() > MAX_QUESTION_LENGTH) {
      throw new IllegalArgumentException("question 过长（上限 " + MAX_QUESTION_LENGTH + " 字符）");
    }
    return q;
  }

  /**
   * 校验 chatMode：仅允许 KNOWLEDGE / LLM；其他显式拒绝（不静默兜底）。
   * 缺省或空白时回落到默认 KNOWLEDGE。
   */
  private static String normalizeChatMode(String raw) {
    if (raw == null || raw.isBlank()) return "KNOWLEDGE";
    String mode = raw.trim().toUpperCase();
    if (!"KNOWLEDGE".equals(mode) && !"LLM".equals(mode)) {
      throw new IllegalArgumentException("chatMode 仅支持 KNOWLEDGE 或 LLM，收到：" + raw);
    }
    return mode;
  }

  /** 解析 thinking 开关：默认 false（快速模式）；传 "true" / "1" / "yes" 则启用思考模式。 */
  private static boolean parseThinking(Map<String, String> body) {
    String v = body.get("thinking");
    if (v == null) return false;
    return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
  }

  // ==================== 会话管理 ====================

  /** 获取用户所有会话列表（一次聚合查询 + 一次批量取标题，无 N+1） */
  @GetMapping("/sessions")
  public List<ChatSession> sessions() {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (userId == null) {
      return List.of();
    }
    List<ChatSession> list = conversationMapper.selectSessionListByUserId(userId);
    if (list.isEmpty()) return list;

    // 一次 HMGET 拉所有自定义标题，避免 N 次 Redis round trip
    RMap<String, String> titleMap = redissonClient.getMap(SESSION_TITLE_KEY);
    java.util.Set<String> ids = new java.util.HashSet<>(list.size());
    for (ChatSession s : list) ids.add(s.getSessionId());
    Map<String, String> customTitles;
    try {
      customTitles = titleMap.getAll(ids);
    } catch (Exception e) {
      log.warn("批量取会话标题失败，回退默认 | err={}", e.getMessage());
      customTitles = java.util.Collections.emptyMap();
    }

    long now = System.currentTimeMillis();
    for (ChatSession s : list) {
      // 标题：用户自定义优先，否则截首条问题
      String customTitle = customTitles.get(s.getSessionId());
      if (customTitle != null && !customTitle.isBlank()) {
        s.setTitle(customTitle);
      } else {
        String fq = s.getFirstQuestion();
        s.setTitle(
            (fq == null || fq.isBlank())
                ? "新对话"
                : (fq.length() > 30 ? fq.substring(0, 30) + "..." : fq));
      }
      if (s.getCreatedAt() == 0L) s.setCreatedAt(now);
    }
    return list;
  }

  /** 重命名会话（鉴权：仅会话归属者可重命名） */
  @PutMapping("/session/{sessionId}/title")
  public Map<String, Object> renameSession(
      @PathVariable String sessionId, @RequestBody Map<String, String> body) {
    requireSessionOwnership(sessionId);
    String title = body.get("title");
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("标题不能为空");
    }
    if (title.length() > 50) {
      title = title.substring(0, 50);
    }
    RMap<String, String> titleMap = redissonClient.getMap(SESSION_TITLE_KEY);
    titleMap.put(sessionId, title);
    return Map.of("sessionId", sessionId, "title", title);
  }

  /** 获取会话历史消息（鉴权：仅会话归属者可查看） */
  @GetMapping("/history/{sessionId}")
  public List<ChatMessage> history(@PathVariable String sessionId) {
    requireSessionOwnership(sessionId);
    return conversationMapper.selectBySession(sessionId);
  }

  /** 删除会话（鉴权：仅会话归属者可删除） */
  @DeleteMapping("/session/{sessionId}")
  public void deleteSession(@PathVariable String sessionId) {
    requireSessionOwnership(sessionId);
    chatHistoryCache.deleteSession(sessionId);
    redissonClient.getMap(SESSION_TITLE_KEY).remove(sessionId);
  }

  /**
   * 校验当前登录用户是否拥有指定会话；不通过则抛 {@link SecurityException}（由
   * {@link com.jianbo.localaiknowledge.config.GlobalExceptionHandler} 兜底返回 403）。
   */
  private void requireSessionOwnership(String sessionId) {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (userId == null) {
      throw new SecurityException("未登录");
    }
    if (!conversationMapper.existsBySessionAndUserId(sessionId, userId)) {
      throw new SecurityException("无权访问该会话");
    }
  }

  /**
   * 聊天入口的鉴权：允许"新会话首次发消息"（DB 中尚不存在该 sessionId）通过， 但若 sessionId 已被他人占用则拒绝，防止越权读取/续写。
   */
  private void assertSessionOwnedByCurrentUser(String sessionId, String userId) {
    if (userId == null) {
      throw new SecurityException("未登录");
    }
    String owner = conversationMapper.selectOwnerOfSession(sessionId);
    if (owner == null) {
      // 新会话：DB 中尚无任何消息，放行（首次发消息会写入并绑定 userId）
      return;
    }
    if (!owner.equals(userId)) {
      throw new SecurityException("无权访问该会话");
    }
  }

  // ==================== 消息反馈 ====================

  /**
   * 用户对单条 assistant 消息的👍/👎反馈。
   *
   * <p>请求体：{@code messageId}（chat_conversation.id）/ {@code rating}（1=赞，-1=踩）/
   * {@code sessionId} / {@code comment?}。同一 user+message 唯一，重复提交即为切换。
   */
  @PostMapping("/feedback")
  public Map<String, Object> feedback(@RequestBody Map<String, Object> body) {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (userId == null) {
      throw new SecurityException("未登录");
    }
    Object midObj = body.get("messageId");
    Object ratingObj = body.get("rating");
    String sessionId = (String) body.get("sessionId");
    String comment = (String) body.get("comment");
    if (midObj == null || ratingObj == null || sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("messageId / rating / sessionId 不能为空");
    }
    long messageId = Long.parseLong(String.valueOf(midObj));
    int rating = Integer.parseInt(String.valueOf(ratingObj));
    if (rating != 1 && rating != -1) {
      throw new IllegalArgumentException("rating 仅支持 1 或 -1");
    }
    requireSessionOwnership(sessionId);
    if (!feedbackMapper.isAssistantMessageInSession(messageId, sessionId)) {
      throw new IllegalArgumentException("该消息不存在或不属于当前会话");
    }
    feedbackMapper.upsert(sessionId, messageId, userId, rating, comment);
    return Map.of("ok", true, "messageId", messageId, "rating", rating);
  }

  // ==================== Prompt 管理 ====================

  /** 获取所有 SystemPrompt */
  @GetMapping("/prompts")
  public List<SystemPrompt> prompts() {
    return promptService.getAll();
  }

  /** 创建或更新 SystemPrompt */
  @PostMapping("/prompt")
  public Map<String, Object> savePrompt(@RequestBody SystemPrompt prompt) {
    if (prompt.getName() == null || prompt.getName().isBlank()) {
      throw new IllegalArgumentException("name 不能为空");
    }
    if (prompt.getContent() == null || prompt.getContent().isBlank()) {
      throw new IllegalArgumentException("content 不能为空");
    }

    SystemPrompt existing = promptService.getByName(prompt.getName());
    if (existing != null) {
      promptService.update(prompt);
      return Map.of("message", "Prompt 已更新", "name", prompt.getName());
    } else {
      if (prompt.getIsDefault() == null) {
        prompt.setIsDefault(false);
      }
      promptService.create(prompt);
      return Map.of("message", "Prompt 已创建", "name", prompt.getName());
    }
  }

  /** 设置默认 Prompt */
  @PutMapping("/prompt/default/{name}")
  public Map<String, Object> setDefault(@PathVariable String name) {
    SystemPrompt prompt = promptService.getByName(name);
    if (prompt == null) {
      throw new IllegalArgumentException("Prompt 不存在");
    }
    promptService.setDefault(name);
    return Map.of("message", "已设为默认 Prompt", "name", name);
  }
}
