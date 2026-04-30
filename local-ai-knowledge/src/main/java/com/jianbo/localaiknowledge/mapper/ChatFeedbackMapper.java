package com.jianbo.localaiknowledge.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ChatFeedbackMapper {

  /**
   * 写入或覆盖一条反馈（同一 user + message 唯一），用于👍/👎切换。
   *
   * <p>依赖 chat_feedback 表上的 {@code uk_feedback_user_msg(user_id, message_id)} 唯一约束。
   */
  @Update(
      """
        INSERT INTO chat_feedback (session_id, message_id, user_id, rating, comment, created_at)
        VALUES (#{sessionId}, #{messageId}, #{userId}, #{rating}, #{comment}, NOW())
        ON CONFLICT (user_id, message_id)
        DO UPDATE SET rating = EXCLUDED.rating,
                      comment = EXCLUDED.comment,
                      created_at = NOW()
    """)
  void upsert(
      @Param("sessionId") String sessionId,
      @Param("messageId") Long messageId,
      @Param("userId") String userId,
      @Param("rating") int rating,
      @Param("comment") String comment);

  /** 校验 message 是否存在并属于指定 session（防止跨 session 提交反馈）。 */
  @Select(
      """
        SELECT EXISTS (
          SELECT 1 FROM chat_conversation
          WHERE id = #{messageId} AND session_id = #{sessionId} AND role = 'assistant'
          LIMIT 1
        )
    """)
  boolean isAssistantMessageInSession(
      @Param("messageId") Long messageId, @Param("sessionId") String sessionId);
}
