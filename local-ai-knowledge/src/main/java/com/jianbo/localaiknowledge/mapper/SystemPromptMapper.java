package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.SystemPrompt;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemPromptMapper {

    @Select("SELECT * FROM system_prompt WHERE is_default = TRUE LIMIT 1")
    SystemPrompt selectDefault();

    @Select("SELECT * FROM system_prompt WHERE name = #{name}")
    SystemPrompt selectByName(@Param("name") String name);

    @Select("SELECT * FROM system_prompt ORDER BY is_default DESC, updated_at DESC")
    List<SystemPrompt> selectAll();

    @Insert("""
        INSERT INTO system_prompt (name, content, description, is_default, created_at, updated_at)
        VALUES (#{name}, #{content}, #{description}, #{isDefault}, NOW(), NOW())
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SystemPrompt prompt);

    @Update("""
        UPDATE system_prompt
        SET content = #{content}, description = #{description}, is_default = #{isDefault}, updated_at = NOW()
        WHERE name = #{name}
    """)
    void update(SystemPrompt prompt);

    @Update("UPDATE system_prompt SET is_default = FALSE WHERE is_default = TRUE")
    void clearDefault();
}
