package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自备 OpenAI 兼容对话模型一行配置，对应表 {@code user_chat_model_config}。
 *
 * <p>运行时通过 {@code model=user:{alias}} 关联；{@link #apiKeyCipher} 为 AES-GCM 密文，不落明文。
 */
@Data
public class UserChatModelConfig {

  private Long id;
  private Long userId;
  /** 与请求体 {@code user:{alias}} 中的 alias 一致，单用户下唯一。 */
  private String alias;
  private String label;
  private String baseUrl;
  /** AES-GCM 加密后的 API Key，由 {@link com.jianbo.localaiknowledge.crypto.UserApiKeyCrypto} 读写。 */
  private String apiKeyCipher;
  private String completionsPath;
  private String model;
  private Double temperature;
  private Integer maxTokens;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
