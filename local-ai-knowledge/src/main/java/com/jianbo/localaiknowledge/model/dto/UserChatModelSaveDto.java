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
//  @NotBlank(message = "别名不能为空")
//  @Size(min = 2, max = 64, message = "别名长度必须在 2-64 之间")
  private String alias;

//  @Size(max = 100, message = "标签不能超过 100 字符")
  private String label;

//  @Size(max = 500, message = "baseUrl 不能超过 500 字符")
  private String baseUrl;

  /** 新建必填；更新时留空表示不改 */
  private String apiKey;

  private String completionsPath;

  /** 新建必填 */
//  @NotBlank(message = "模型不能为空")
  private String model;

  private Double temperature;

  private Integer maxTokens;
}
