package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/** System Prompt 配置（持久化到 DB，支持多套 Prompt 动态切换） */
@Data
public class SystemPrompt {

  private Long id;

  /** Prompt 名称（唯一标识） */
  private String name;

  /** Prompt 内容（包含 {context} 占位符） */
  private String content;

  /** 描述说明 */
  private String description;

  /** 是否为默认 Prompt */
  private Boolean isDefault;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
