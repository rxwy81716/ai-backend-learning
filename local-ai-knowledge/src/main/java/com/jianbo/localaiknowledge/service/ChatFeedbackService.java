package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.config.ForbiddenException;
import com.jianbo.localaiknowledge.mapper.ChatFeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 聊天反馈服务
 *
 * <p>包含消息反馈业务逻辑，从 RagController 下沉而来。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatFeedbackService {

  private final ChatFeedbackMapper feedbackMapper;
  private final ChatHistoryCacheService chatHistoryCache;

  /**
   * 用户对单条 assistant 消息的👍/👎反馈。
   *
   * @param messageId 消息ID
   * @param rating 评分（1=赞，-1=踩）
   * @param sessionId 会话ID
   * @param userId 用户ID
   * @param comment 评价（可选）
   */
  public Map<String, Object> feedback(Long messageId, int rating, String sessionId, String userId, String comment) {
    if (messageId == null || rating == 0 || sessionId == null || sessionId.isBlank()) {
      throw new IllegalArgumentException("messageId / rating / sessionId 不能为空");
    }
    if (rating != 1 && rating != -1) {
      throw new IllegalArgumentException("rating 仅支持 1 或 -1");
    }
    // 鉴权：校验会话归属
    if (!chatHistoryCache.isSessionOwnedBy(sessionId, userId)) {
      throw new ForbiddenException("无权访问该会话");
    }
    // 校验消息属于该会话
    if (!feedbackMapper.isAssistantMessageInSession(messageId, sessionId)) {
      throw new IllegalArgumentException("该消息不存在或不属于当前会话");
    }
    feedbackMapper.upsert(sessionId, messageId, userId, rating, comment);
    log.debug("消息反馈 | messageId={}, rating={}, userId={}", messageId, rating, userId);
    return Map.of("ok", true, "messageId", messageId, "rating", rating);
  }
}
