package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.ChatConversationMapper;
import com.jianbo.localaiknowledge.model.ChatMessage;
import com.jianbo.localaiknowledge.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多轮对话历史缓存服务（Redis 热缓存 + DB 持久化）
 *
 * <p>策略： 读：Redis → miss → DB → 回填 Redis 写：先 DB 后 Redis（DB 失败抛异常，Redis 失败兜底 evict）
 * 过期：Redis TTL 4 小时 + 读时续期（不活跃会话自然清理） 删除：同时清 Redis + DB
 *
 * <p>高并发优势： - 热会话命中 Redis，~1ms 响应 - DB 只在首次 miss 时查一次 - 即使 Redis 挂了，DB 兜底不丢数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatHistoryCacheService {

  private final ChatConversationMapper conversationMapper;
  private final RedisTemplate<String, Object> redisTemplate;

  private static final String KEY_PREFIX = "chat:session:";
  // 4 小时窗口：覆盖典型用户"短暂离开继续聊"的场景；读时续期已在 loadRecentHistory 内做，
  // 长期不活跃的会话仍会被自然过期不占内存。
  private static final long TTL_MINUTES = 240;

  /**
   * 保存一条消息（Redis + DB 双写）。
   *
   * <p>调用方保证在可阻塞线程上调用：同步 chat() 走请求线程；流式 chatStream() 的 doOnComplete 通过
   * {@code subscribeOn(boundedElastic())} 已经在 boundedElastic 上。
   *
   * <p>一致性：先写 DB（失败抛异常，由调用方决定如何处理），再写 Redis；Redis 失败时主动 evict
   * 该 session 缓存，避免后续读取到“缺这条”的旧 Redis。
   */
  public void saveMessage(ChatMessage message) {
    String key = KEY_PREFIX + message.getSessionId();

    // 1. 先 DB（持久层为准），失败直接抛出，不污染 Redis
    conversationMapper.insert(message);

    // 2. 再写 Redis；Redis 失败兜底删 key，下次 load 走 DB 重建，避免缓存与 DB 不一致
    try {
      redisTemplate.opsForList().rightPush(key, message);
      redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
    } catch (Exception e) {
      log.error(
          "Redis 写入失败，清除缓存以保证一致性 | session={}, error={}",
          message.getSessionId(),
          e.getMessage());
      try {
        redisTemplate.delete(key);
      } catch (Exception ignore) {
        // Redis 不可用时 delete 也会抛，吞掉即可，下次读自然 miss 走 DB
      }
    }

    log.debug("消息已保存 | session={}, role={}", message.getSessionId(), message.getRole());
  }

  /**
   * 加载最近 N 条历史（Redis → miss → DB → 回填）
   *
   * @return 按时间正序排列的消息列表
   */
  public List<ChatMessage> loadRecentHistory(String sessionId, int limit) {
    String key = KEY_PREFIX + sessionId;

    // 1. 尝试从 Redis 读取
    Long size = redisTemplate.opsForList().size(key);
    if (size != null && size > 0) {
      long start = Math.max(0, size - limit);
      List<Object> rawList = redisTemplate.opsForList().range(key, start, size - 1);
      if (rawList != null && !rawList.isEmpty()) {
        log.debug("Redis 命中 | session={}, count={}", sessionId, rawList.size());
        // 续期
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        return RedisUtil.toList(rawList, ChatMessage.class);
      }
    }

    // 2. Redis miss，从 DB 加载
    List<ChatMessage> history = conversationMapper.selectRecentBySession(sessionId, limit);
    if (history.isEmpty()) {
      return List.of();
    }

    // DB 返回 DESC，翻转为 ASC
    Collections.reverse(history);

    // 3. 回填 Redis
    for (ChatMessage msg : history) {
      redisTemplate.opsForList().rightPush(key, msg);
    }
    redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
    log.debug("DB → Redis 回填 | session={}, count={}", sessionId, history.size());

    return history;
  }

  /** 删除会话（Redis + DB 双删） */
  public void deleteSession(String sessionId) {
    conversationMapper.deleteBySession(sessionId);
    redisTemplate.delete(KEY_PREFIX + sessionId);
    log.info("会话已删除 | session={}", sessionId);
  }

  /** 清除某会话的 Redis 缓存（DB 不动） 用于缓存不一致时手动修复 */
  public void evictCache(String sessionId) {
    redisTemplate.delete(KEY_PREFIX + sessionId);
  }
}
