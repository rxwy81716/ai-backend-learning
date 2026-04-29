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

  /** 批量插入分段 */
  default void batchInsert(List<DocumentChunk> chunks) {
    for (DocumentChunk chunk : chunks) {
      insert(chunk);
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
