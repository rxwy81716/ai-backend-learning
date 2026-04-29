package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.ChatConversationMapper;
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

  private static final String SESSION_TITLE_KEY = "chat:session:titles";

  // ==================== 智能问答 ====================

  /**
   * 智能问答（同步）
   *
   * @param chatMode 问答模式：KNOWLEDGE=知识库模式（默认） / LLM=LLM直答模式
   */
  @PostMapping("/chat")
  public Map<String, Object> chat(@RequestBody Map<String, String> body) {
    String question = body.get("question");
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question 不能为空");
    }
    String sessionId = body.get("sessionId");
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString().replace("-", "");
    }
    String userId = SecurityUtil.getCurrentUserIdStr();
    String promptName = body.get("promptName");
    String chatMode = body.getOrDefault("chatMode", "KNOWLEDGE");

    return ragAgentService.chat(sessionId, question, userId, promptName, chatMode);
  }

  /**
   * 智能问答（SSE 流式） 注意：SSE 流不被统一响应包装
   *
   * @param chatMode 问答模式：KNOWLEDGE=知识库模式（默认） / LLM=LLM直答模式
   */
  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> chatStream(@RequestBody Map<String, String> body) {
    String question = body.get("question");
    if (question == null || question.isBlank()) {
      return Flux.just("[ERROR] question 不能为空");
    }
    String sessionId = body.get("sessionId");
    if (sessionId == null || sessionId.isBlank()) {
      sessionId = UUID.randomUUID().toString().replace("-", "");
    }
    String userId = SecurityUtil.getCurrentUserIdStr();
    String promptName = body.get("promptName");
    String chatMode = body.getOrDefault("chatMode", "KNOWLEDGE");

    return ragAgentService.chatStream(sessionId, question, userId, promptName, chatMode);
  }

  // ==================== 会话管理 ====================

  /** 获取用户所有会话列表（一次聚合查询，无 N+1） */
  @GetMapping("/sessions")
  public List<ChatSession> sessions() {
    String userId = SecurityUtil.getCurrentUserIdStr();
    if (userId == null) {
      return List.of();
    }
    List<ChatSession> list = conversationMapper.selectSessionListByUserId(userId);
    if (list.isEmpty()) return list;

    RMap<String, String> titleMap = redissonClient.getMap(SESSION_TITLE_KEY);
    long now = System.currentTimeMillis();
    for (ChatSession s : list) {
      // 标题：用户自定义优先，否则截首条问题
      String customTitle = titleMap.get(s.getSessionId());
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
