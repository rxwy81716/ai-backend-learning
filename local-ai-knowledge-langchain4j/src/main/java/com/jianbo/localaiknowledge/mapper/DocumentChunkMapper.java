package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.DocumentChunk;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DocumentChunkMapper {
    @Insert("""
        INSERT INTO document_chunk (task_id, chunk_index, content, source, user_id, doc_scope, created_at)
        VALUES (#{taskId}, #{chunkIndex}, #{content}, #{source}, #{userId}, #{docScope}, NOW())
    """)
    void insert(DocumentChunk chunk);

    @Select("SELECT * FROM document_chunk WHERE task_id = #{taskId} ORDER BY chunk_index ASC")
    List<DocumentChunk> selectByTaskId(@Param("taskId") String taskId);

    @Delete("DELETE FROM document_chunk WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);
}
