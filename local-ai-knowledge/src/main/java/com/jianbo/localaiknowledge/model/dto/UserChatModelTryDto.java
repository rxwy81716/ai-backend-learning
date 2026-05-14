package com.jianbo.localaiknowledge.model.dto;

import lombok.Data;

/**
 * 仅用于「测试连接」接口：明文参数，不落库、不写审计外的持久化。
 *
 * @see com.jianbo.localaiknowledge.controller.UserChatModelController#tryInline
 */
@Data
public class UserChatModelTryDto {

  private String baseUrl;
  private String apiKey;
  private String completionsPath;
  private String model;
  private Double temperature;
  private Integer maxTokens;
}
