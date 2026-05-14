package com.jianbo.localaiknowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 用户 Chat 配置变更时，通过 Redis Pub/Sub 通知所有应用实例清理本地 {@code ChatClient} 缓存。
 *
 * <p>不把 {@link org.springframework.ai.chat.client.ChatClient} 本体放进 Redis（不可序列化、且含连接状态），
 * 仅广播失效信号；各节点内存 map 仍保留实际客户端实例。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserChatModelEvictPublisher {

  /** 与 {@link UserChatModelEvictSubscriber} 保持一致 */
  public static final String TOPIC = "local-ai:user-chat-model-evict";

  private final RedissonClient redissonClient;

  /** 消息格式：{@code userId:alias} */
  public void publish(Long userId, String alias) {
    if (userId == null || alias == null || alias.isBlank()) {
      return;
    }
    try {
      redissonClient.getTopic(TOPIC).publishAsync(userId + ":" + alias);
    } catch (Exception e) {
      log.warn("[UserChatModelEvictPublisher] Redis 广播失败（单机可忽略）: {}", e.getMessage());
    }
  }
}
