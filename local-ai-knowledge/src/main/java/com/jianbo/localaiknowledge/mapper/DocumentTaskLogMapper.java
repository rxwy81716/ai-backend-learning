package com.jianbo.localaiknowledge.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentTaskLogMapper {

  @Insert(
      """
        INSERT INTO document_task_log (task_id, action, detail, created_at)
        VALUES (#{taskId}, #{action}, #{detail}, NOW())
    """)
  void insert(
      @Param("taskId") String taskId,
      @Param("action") String action,
      @Param("detail") String detail);

  @Select("SELECT * FROM document_task_log WHERE task_id = #{taskId} ORDER BY created_at ASC")
  List<Map<String, Object>> selectByTaskId(@Param("taskId") String taskId);
}
