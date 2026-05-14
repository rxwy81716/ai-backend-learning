package com.jianbo.localaiknowledge.model.dto;

import lombok.Data;

/**
 * 保存用户自备 Chat 配置的请求体：新建带 {@link #alias}；更新带 {@link #id}，{@link #apiKey} 可空表示不改密文。
 */
@Data
public class UserChatModelSaveDto {

  /** 更新时传 */
  private Long id;

  /** 新建必填；仅字母数字下划线与中划线，2~64 */
  private String alias;

  private String label;
  private String baseUrl;
  /** 新建必填；更新时留空表示不改 */
  private String apiKey;
  private String completionsPath;
  private String model;
  private Double temperature;
  private Integer maxTokens;
}
