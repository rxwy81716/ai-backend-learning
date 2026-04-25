package com.jianbo.springai.session;

import com.jianbo.springai.entity.ChatMsg;
import com.jianbo.springai.utils.RedisUtil;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@AllArgsConstructor
public class ChatSessionCache {
  private final RedisTemplate<String, Object> redisTemplate;
  // 过期时间，单位小时
  private static final long EXPIRE_TIME = 12;

  private static final String SESSION_KEY_PREFIX = "ai:session:";

  // 读取会话历史
  public List<ChatMsg> getHistory(String sessionId) {
    String key = SESSION_KEY_PREFIX + sessionId;
    Object result = redisTemplate.opsForValue().get(key);
    return RedisUtil.toList(result, ChatMsg.class);
  }

  // 保存会话历史/更新会话历史
  public void saveHistory(String sessionId, List<ChatMsg> history) {
    String key = SESSION_KEY_PREFIX + sessionId;
    redisTemplate.opsForValue().set(key, history, EXPIRE_TIME, TimeUnit.HOURS);
  }

  public void clearSession(String sessionId) {
    redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
  }
}
