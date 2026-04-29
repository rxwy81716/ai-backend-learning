package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.DocumentTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 文档任务 Mapper（依赖 mybatis.configuration.map-underscore-to-camel-case=true 自动映射）
 */
@Mapper
public interface DocumentTaskMapper {

    @Insert("""
        INSERT INTO document_task (task_id, user_id, doc_scope, file_name, file_path, file_size, status, created_at, updated_at)
        VALUES (#{taskId}, #{userId}, #{docScope}, #{fileName}, #{filePath}, #{fileSize}, #{status}, #{createdAt}, #{createdAt})
    """)
    void insert(DocumentTask task);

    @Update("""
        UPDATE document_task
        SET status = #{status},
            total_chunks = #{totalChunks},
            imported_chunks = #{importedChunks},
            error_msg = #{errorMsg},
            finished_at = #{finishedAt},
            updated_at = NOW()
        WHERE task_id = #{taskId}
    """)
    void update(DocumentTask task);

    @Select("SELECT * FROM document_task WHERE task_id = #{taskId}")
    DocumentTask selectByTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM document_task where user_id = #{userId}  ORDER BY created_at DESC")
    List<DocumentTask> selectAllUserTasks(String userId);

    @Select("SELECT * FROM document_task WHERE doc_scope = 'PUBLIC' OR user_id = #{userId} ORDER BY created_at DESC")
    List<DocumentTask> selectAccessibleTasks(@Param("userId") String userId);

    @Select("SELECT * FROM document_task ORDER BY created_at DESC")
    List<DocumentTask> selectAll();

    @Delete("DELETE FROM document_task WHERE task_id = #{taskId}")
    int deleteByTaskId(@Param("taskId") String taskId);
}
