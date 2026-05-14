package com.jianbo.localaiknowledge.service.agent;

/**
 * 用户自备 Chat 与系统内置 model key 的命名隔离：前端 / SSE 请求体使用 {@code user:{alias}}，
 * 与 glm、deepseek、default 等系统 key 不冲突。
 */
public final class UserChatModelKeys {

  /** 前缀固定为 {@value #PREFIX}，alias 为库表 {@code user_chat_model_config.alias}。 */
  public static final String PREFIX = "user:";

  private UserChatModelKeys() {}

  /** 供 {@code GET /api/rag/models} 合并展示用，例如 alias=my → {@code user:my}。 */
  public static String prefixed(String alias) {
    return PREFIX + alias;
  }

  /** 是否为 {@code user:xxx} 且 xxx 非空。 */
  public static boolean isUserModelKey(String modelKey) {
    return modelKey != null
        && modelKey.startsWith(PREFIX)
        && modelKey.length() > PREFIX.length()
        && !modelKey.substring(PREFIX.length()).isBlank();
  }

  /** 从完整 modelKey 解析 alias；调用前应先 {@link #isUserModelKey(String)}。 */
  public static String parseAlias(String modelKey) {
    return modelKey.substring(PREFIX.length()).trim();
  }
}
