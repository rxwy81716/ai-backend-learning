package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.DocumentChunk;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 文档分段 Mapper */
@Mapper
public interface DocumentChunkMapper {

  @Insert(
      """
        INSERT INTO document_chunk (task_id, chunk_index, content, source, user_id, doc_scope, created_at)
        VALUES (#{taskId}, #{chunkIndex}, #{content}, #{source}, #{userId}, #{docScope}, NOW())
    """)
  void insert(DocumentChunk chunk);

  @Insert("<script>" +
      "INSERT INTO document_chunk (task_id, chunk_index, content, source, user_id, doc_scope, created_at) VALUES " +
      "<foreach collection='list' item='chunk' separator=','>" +
      "(#{chunk.taskId}, #{chunk.chunkIndex}, #{chunk.content}, #{chunk.source}, " +
      "#{chunk.userId}, #{chunk.docScope}, NOW())" +
      "</foreach>" +
      "</script>")
  void insertBatch(List<DocumentChunk> chunks);

  /** 批量插入分段（100 条/批，一次 INSERT 多 VALUES，比逐条 insert 减少 99% DB 往返） */
  default void batchInsert(List<DocumentChunk> chunks) {
    if (chunks == null || chunks.isEmpty()) return;
    int subBatchSize = 100;  // 减小批量大小，避免远程 PG 连接 I/O 错误
    for (int i = 0; i < chunks.size(); i += subBatchSize) {
      int end = Math.min(i + subBatchSize, chunks.size());
      List<DocumentChunk> subBatch = chunks.subList(i, end);
      
      // 添加重试机制，最多重试 3 次
      int maxRetries = 3;
      for (int retry = 0; retry < maxRetries; retry++) {
        try {
          insertBatch(subBatch);
          break;  // 成功则跳出重试循环
        } catch (Exception e) {
          if (retry == maxRetries - 1) {
            // 最后一次重试失败，抛出异常
            throw new RuntimeException("批量插入失败，已重试 " + maxRetries + " 次", e);
          }
          // 等待后重试
          try {
            Thread.sleep(1000 * (retry + 1));  // 指数退避
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("批量插入被中断", ie);
          }
        }
      }
    }
  }

  @Select("SELECT * FROM document_chunk WHERE task_id = #{taskId} ORDER BY chunk_index ASC")
  List<DocumentChunk> selectByTaskId(@Param("taskId") String taskId);

  @Select("SELECT * FROM document_chunk WHERE source = #{source} ORDER BY chunk_index ASC")
  List<DocumentChunk> selectBySource(@Param("source") String source);

  @Delete("DELETE FROM document_chunk WHERE task_id = #{taskId}")
  int deleteByTaskId(@Param("taskId") String taskId);

  @Delete("DELETE FROM document_chunk WHERE source = #{source}")
  int deleteBySource(@Param("source") String source);

  @Select("SELECT COUNT(*) FROM document_chunk WHERE task_id = #{taskId}")
  int countByTaskId(@Param("taskId") String taskId);
}
