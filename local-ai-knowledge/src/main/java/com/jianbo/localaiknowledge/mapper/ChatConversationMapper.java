package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatConversationMapper {

    @Insert("""
        INSERT INTO chat_conversation (session_id, user_id, role, content, metadata, created_at)
        VALUES (#{sessionId}, #{userId}, #{role}, #{content}, #{metadata}::jsonb, NOW())
    """)
    void insert(ChatMessage message);

    @Select("""
        SELECT * FROM chat_conversation
        WHERE session_id = #{sessionId}
        ORDER BY created_at ASC
    """)
    List<ChatMessage> selectBySession(@Param("sessionId") String sessionId);

    @Select("""
        SELECT * FROM chat_conversation
        WHERE session_id = #{sessionId}
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
    List<ChatMessage> selectRecentBySession(@Param("sessionId") String sessionId,
                                             @Param("limit") int limit);

    @Select("""
        SELECT DISTINCT session_id FROM chat_conversation
        ORDER BY session_id
    """)
    List<String> selectAllSessionIds();

    @Delete("DELETE FROM chat_conversation WHERE session_id = #{sessionId}")
    void deleteBySession(@Param("sessionId") String sessionId);
}
