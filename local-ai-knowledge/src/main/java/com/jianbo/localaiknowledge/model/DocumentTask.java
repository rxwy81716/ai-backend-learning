package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/** 文档解析任务（内存态，跟踪上传→解析→入库全流程） */
@Data
public class DocumentTask {

  /** 任务ID（UUID） */
  private String taskId;

  /** 上传用户 ID（未登录可为 null） */
  private String userId;

  /** 文档范围：PRIVATE=用户私有 / PUBLIC=公共（爬虫） */
  private String docScope;

  /** 原始文件名 */
  private String fileName;

  /** 本地保存路径 */
  private String filePath;

  /** 文件大小（字节） */
  private long fileSize;

  /** 任务状态 */
  private TaskStatus status;

  /** 解析出的切片总数 */
  private int totalChunks;

  /** 已入库切片数 */
  private int importedChunks;

  /** 错误信息（失败时填写） */
  private String errorMsg;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 完成时间 */
  private LocalDateTime finishedAt;

  public enum TaskStatus {
    /** 已上传，等待解析 */
    UPLOADED,
    /** 正在解析文档（Tika） */
    PARSING,
    /** 正在向量化入库 */
    IMPORTING,
    /** 完成 */
    DONE,
    /** 失败 */
    FAILED
  }

  /** 便捷构造 */
  public static DocumentTask create(
      String taskId, String fileName, String filePath, long fileSize) {
    return create(taskId, fileName, filePath, fileSize, null, "PUBLIC");
  }

  public static DocumentTask create(
      String taskId,
      String fileName,
      String filePath,
      long fileSize,
      String userId,
      String docScope) {
    DocumentTask task = new DocumentTask();
    task.setTaskId(taskId);
    task.setFileName(fileName);
    task.setFilePath(filePath);
    task.setFileSize(fileSize);
    task.setUserId(userId);
    task.setDocScope(docScope != null ? docScope : "PUBLIC");
    task.setStatus(TaskStatus.UPLOADED);
    task.setCreatedAt(LocalDateTime.now());
    return task;
  }
}
