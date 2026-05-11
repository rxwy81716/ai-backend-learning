package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.ChatMessage;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChatMessageMapper {
    @Insert("""
        INSERT INTO chat_conversation (session_id, user_id, role, content, metadata, created_at)
        VALUES (#{sessionId}, #{userId}, #{role}, #{content}, #{metadata}, #{createdAt})
    """)
    void insert(ChatMessage msg);

    @Select("SELECT * FROM chat_conversation WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> selectBySessionId(@Param("sessionId") String sessionId);

    @Delete("DELETE FROM chat_conversation WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);
}
