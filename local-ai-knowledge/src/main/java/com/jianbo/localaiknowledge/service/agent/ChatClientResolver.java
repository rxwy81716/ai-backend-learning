package com.jianbo.localaiknowledge.service.agent;

import com.jianbo.localaiknowledge.crypto.UserApiKeyCrypto;
import com.jianbo.localaiknowledge.mapper.UserChatModelConfigMapper;
import com.jianbo.localaiknowledge.model.UserChatModelConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时按请求中的 {@code model} 解析要使用的 {@link ChatClient}。
 *
 * <p><b>解析顺序</b>：{@code model} 为空 → {@link ChatModelRegistry} 默认；以 {@code user:} 开头 →
 * 读库解密后构建用户自备的 OpenAI 兼容客户端；否则 → 走 YAML 注册的系统 key（glm/deepseek 等）。
 *
 * <p><b>缓存</b>：用户侧 {@link ChatClient} 缓存在本机 {@link ConcurrentHashMap}（按 userId/alias），
 * 避免每次 SSE 重建连接。Embedding 与向量库仍全局默认，不在此解析。
 *
 * <p>配置变更后须 {@link #evictUserModel(Long, String)}；多实例时由 {@link com.jianbo.localaiknowledge.service.UserChatModelEvictPublisher}
 * 经 Redis Topic 通知各节点一并失效。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatClientResolver {

  private final ChatModelRegistry chatModelRegistry;
  private final UserChatModelConfigMapper userChatModelConfigMapper;
  private final UserApiKeyCrypto userApiKeyCrypto;
  private final OpenAiCompatibleChatModelFactory openAiChatModelFactory;

  /** 仅缓存「用户自备」ChatClient；key = userId + "/" + alias（非 Redis，见类说明）。 */
  private final ConcurrentHashMap<String, ChatClient> userClientCache = new ConcurrentHashMap<>();

  private static String cacheKey(Long userId, String alias) {
    return userId + "/" + alias;
  }

  public void evictUserModel(Long userId, String alias) {
    if (userId == null || alias == null || alias.isBlank()) {
      return;
    }
    userClientCache.remove(cacheKey(userId, alias));
  }

  /**
   * @param userIdStr 当前用户 ID 字符串；未登录时若指定 {@code user:} 模型会回落到默认
   */
  public ChatClient resolve(String userIdStr, String modelKey) {
    if (modelKey == null || modelKey.isBlank()) {
      return chatModelRegistry.getClient(null);
    }
    if (UserChatModelKeys.isUserModelKey(modelKey)) {
      String alias = UserChatModelKeys.parseAlias(modelKey);
      if (userIdStr == null || userIdStr.isBlank()) {
        log.warn("[ChatClientResolver] 未登录却请求 {}，fallback default", modelKey);
        return chatModelRegistry.getClient(null);
      }
      Long userId;
      try {
        userId = Long.parseLong(userIdStr);
      } catch (NumberFormatException e) {
        log.warn("[ChatClientResolver] userId 非法 userIdStr={}，fallback default", userIdStr);
        return chatModelRegistry.getClient(null);
      }
      return resolveUserModel(userId, alias);
    }
    return chatModelRegistry.getClient(modelKey);
  }

  /** 查库 → 解密 Key → 构建客户端；未命中或库中无行则回落默认，且不把「无配置」写入缓存。 */
  private ChatClient resolveUserModel(Long userId, String alias) {
    String ck = cacheKey(userId, alias);
    ChatClient cached = userClientCache.get(ck);
    if (cached != null) {
      return cached;
    }
    UserChatModelConfig row = userChatModelConfigMapper.selectByUserAndAlias(userId, alias);
    if (row == null) {
      log.warn("[ChatClientResolver] 用户模型不存在 userId={} alias={}，fallback default", userId, alias);
      return chatModelRegistry.getClient(null);
    }
    String apiKey = userApiKeyCrypto.decrypt(row.getApiKeyCipher());
    ChatClient c =
        openAiChatModelFactory.createChatClient(
            row.getBaseUrl(),
            apiKey,
            row.getCompletionsPath(),
            row.getModel(),
            row.getTemperature(),
            row.getMaxTokens());
    // 配置稳定期内复用同一 ChatClient；更新配置后依赖 evict + 可选 Redis 广播
    userClientCache.put(ck, c);
    return c;
  }
}
