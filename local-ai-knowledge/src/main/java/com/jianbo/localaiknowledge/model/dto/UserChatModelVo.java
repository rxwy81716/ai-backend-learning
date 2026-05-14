package com.jianbo.localaiknowledge.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 返回给前端的用户 Chat 配置视图：不含明文 Key，仅 {@link #apiKeyHint} 脱敏片段。
 */
@Data
@Builder
public class UserChatModelVo {

  private Long id;
  private String alias;
  private String label;
  private String baseUrl;
  private String completionsPath;
  private String model;
  private Double temperature;
  private Integer maxTokens;
  /** 是否已配置过 API Key（更新时可不重复提交） */
  private boolean apiKeyConfigured;
  /** 脱敏展示，如 sk-***ab12 */
  private String apiKeyHint;
}
