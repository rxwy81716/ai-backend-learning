package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.service.agent.ChatClientResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 订阅 {@link UserChatModelEvictPublisher#TOPIC}：多实例部署时，任一节点修改用户 Chat 配置后，
 * 其余节点收到消息后对本机 {@link ChatClientResolver} 做相同 evict，避免继续使用旧客户端。
 *
 * <p>注意：发布方本机也会收到一条消息，重复 evict 为幂等操作。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserChatModelEvictSubscriber {

  private final RedissonClient redissonClient;
  private final ChatClientResolver chatClientResolver;

  @PostConstruct
  void subscribe() {
    redissonClient
        .getTopic(UserChatModelEvictPublisher.TOPIC)
        .addListener(
            String.class,
            (MessageListener<String>) (channel, msg) -> applyEvict(msg));
    log.info("✅ [UserChatModelEvictSubscriber] 已订阅 Redis Topic: {}", UserChatModelEvictPublisher.TOPIC);
  }

  private void applyEvict(String msg) {
    if (msg == null || msg.isBlank()) {
      return;
    }
    int idx = msg.indexOf(':');
    if (idx < 1 || idx >= msg.length() - 1) {
      return;
    }
    try {
      Long userId = Long.parseLong(msg.substring(0, idx));
      String alias = msg.substring(idx + 1).trim();
      chatClientResolver.evictUserModel(userId, alias);
      log.debug("[UserChatModelEvictSubscriber] evict userId={} alias={}", userId, alias);
    } catch (NumberFormatException e) {
      log.warn("[UserChatModelEvictSubscriber] 非法消息: {}", msg);
    }
  }
}
