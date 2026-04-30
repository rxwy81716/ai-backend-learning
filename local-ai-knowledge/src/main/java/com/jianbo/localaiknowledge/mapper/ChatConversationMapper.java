package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.ChatMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ChatConversationMapper {

  @Insert(
      """
        INSERT INTO chat_conversation (session_id, user_id, role, content, metadata, created_at)
        VALUES (#{sessionId}, #{userId}, #{role}, #{content}, #{metadata}::jsonb, NOW())
    """)
  void insert(ChatMessage message);

  @Select(
      """
        SELECT * FROM chat_conversation
        WHERE session_id = #{sessionId}
        ORDER BY created_at ASC
    """)
  List<ChatMessage> selectBySession(@Param("sessionId") String sessionId);

  @Select(
      """
        SELECT * FROM chat_conversation
        WHERE session_id = #{sessionId}
        ORDER BY created_at DESC
        LIMIT #{limit}
    """)
  List<ChatMessage> selectRecentBySession(
      @Param("sessionId") String sessionId, @Param("limit") int limit);

  @Select(
      """
        SELECT DISTINCT session_id FROM chat_conversation
        where user_id = #{userId}
        ORDER BY session_id
    """)
  List<String> selectByUserId(@Param("userId") String userId);

  @Select(
      """
        SELECT content FROM chat_conversation
        WHERE session_id = #{sessionId} AND role = 'user'
        ORDER BY created_at ASC
        LIMIT 1
    """)
  String selectFirstQuestion(@Param("sessionId") String sessionId);

  @Select(
      """
        SELECT EXTRACT(EPOCH FROM MIN(created_at)) * 1000::bigint
        FROM chat_conversation
        WHERE session_id = #{sessionId}
    """)
  Long selectCreatedAt(@Param("sessionId") String sessionId);

  /**
   * 一次拉取用户所有会话的列表数据（避免 N+1 query）。
   *
   * <p>列：session_id / first_question（最早的 user 消息）/ created_at_ms（会话首条消息时间）
   */
  @Select(
      """
        SELECT
          c.session_id            AS sessionId,
          (SELECT content FROM chat_conversation
              WHERE session_id = c.session_id AND role = 'user'
              ORDER BY created_at ASC LIMIT 1) AS firstQuestion,
          (EXTRACT(EPOCH FROM MIN(c.created_at)) * 1000)::bigint AS createdAt
        FROM chat_conversation c
        WHERE c.user_id = #{userId}
        GROUP BY c.session_id
        ORDER BY createdAt DESC
    """)
  List<com.jianbo.localaiknowledge.model.ChatSession> selectSessionListByUserId(
      @Param("userId") String userId);

  /** 校验 sessionId 是否属于指定 userId（用于鉴权） */
  @Select(
      """
        SELECT EXISTS (
          SELECT 1 FROM chat_conversation
          WHERE session_id = #{sessionId} AND user_id = #{userId}
          LIMIT 1
        )
    """)
  boolean existsBySessionAndUserId(
      @Param("sessionId") String sessionId, @Param("userId") String userId);

  /**
   * 查询 sessionId 当前归属的 userId（任意一条消息的 user_id）。
   *
   * <p>用于聊天入口的鉴权：
   *
   * <ul>
   *   <li>返回 {@code null}：该 sessionId 在 DB 中尚不存在（新会话首次发消息），放行
   *   <li>返回值 == 当前 userId：本人会话，放行
   *   <li>返回值 != 当前 userId：他人会话，拒绝 403
   * </ul>
   */
  @Select(
      """
        SELECT user_id FROM chat_conversation
        WHERE session_id = #{sessionId}
        LIMIT 1
    """)
  String selectOwnerOfSession(@Param("sessionId") String sessionId);

  @Delete("DELETE FROM chat_conversation WHERE session_id = #{sessionId}")
  void deleteBySession(@Param("sessionId") String sessionId);
}
