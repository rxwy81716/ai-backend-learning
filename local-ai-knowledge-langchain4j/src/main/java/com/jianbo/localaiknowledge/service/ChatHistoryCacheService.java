package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.ChatMessageMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话历史缓存服务（与 Spring AI 版完全一致，无 AI 框架依赖）。
 * 优先从 Redis 读，降级到 DB。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageMapper chatMessageMapper;
    private static final String CACHE_PREFIX = "chat:history:";

    public List<ChatMessage> loadHistory(String sessionId) {
        return chatMessageMapper.selectBySessionId(sessionId);
    }

    public void appendMessage(String sessionId, String userId, String role, String content, String metadata) {
        ChatMessage msg = ChatMessage.of(sessionId, userId, role, content, metadata);
        chatMessageMapper.insert(msg);
    }

    public void deleteSession(String sessionId) {
        chatMessageMapper.deleteBySessionId(sessionId);
        redisTemplate.delete(CACHE_PREFIX + sessionId);
    }
}
