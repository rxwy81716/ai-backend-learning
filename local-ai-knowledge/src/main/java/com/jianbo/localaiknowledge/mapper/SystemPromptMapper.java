package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.SystemPrompt;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SystemPromptMapper {

    @Select("SELECT id, name, content, description, is_default, created_at, updated_at FROM system_prompt WHERE is_default = TRUE LIMIT 1")
    @Results({
        @Result(property = "isDefault", column = "is_default"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    SystemPrompt selectDefault();

    @Select("SELECT id, name, content, description, is_default, created_at, updated_at FROM system_prompt WHERE name = #{name}")
    @Results({
        @Result(property = "isDefault", column = "is_default"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    SystemPrompt selectByName(@Param("name") String name);

    @Select("SELECT id, name, content, description, is_default, created_at, updated_at FROM system_prompt ORDER BY is_default DESC, updated_at DESC")
    @Results({
        @Result(property = "isDefault", column = "is_default"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
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

    @Select("SELECT id, name, content, description, is_default, created_at, updated_at FROM system_prompt WHERE id = #{id}")
    @Results({
        @Result(property = "isDefault", column = "is_default"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "updatedAt", column = "updated_at")
    })
    SystemPrompt selectById(@Param("id") Long id);

    @Update("""
        UPDATE system_prompt
        SET name = #{name}, content = #{content}, description = #{description},
            is_default = #{isDefault}, updated_at = NOW()
        WHERE id = #{id}
    """)
    void updateById(SystemPrompt prompt);

    @Delete("DELETE FROM system_prompt WHERE id = #{id}")
    void deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM system_prompt")
    int count();
}
