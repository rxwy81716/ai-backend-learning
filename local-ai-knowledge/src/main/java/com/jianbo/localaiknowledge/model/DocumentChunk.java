package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分段实体（存储到 PG document_chunk 表）
 *
 * <p>每个分段对应一段原文，同时向量会写入 vector_store 表（由 PgVectorStore 管理） 查询优先走 ES 向量检索，PG document_chunk
 * 作为分段原文的持久化备份
 */
@Data
public class DocumentChunk {

  private Long id;

  /** 关联文档任务ID */
  private String taskId;

  /** 分段序号（从0开始） */
  private Integer chunkIndex;

  /** 分段原文 */
  private String content;

  /** 文档来源名（文件名） */
  private String source;

  /** 上传用户ID */
  private String userId;

  /** 文档范围：PRIVATE/PUBLIC */
  private String docScope;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
