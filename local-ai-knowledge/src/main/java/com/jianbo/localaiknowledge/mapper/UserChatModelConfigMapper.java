package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.UserChatModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 用户自备 Chat 配置表 {@code user_chat_model_config} 的 MyBatis 访问层。 */
@Mapper
public interface UserChatModelConfigMapper {

  @Select(
      """
      SELECT id, user_id, alias, label, base_url, api_key_cipher, completions_path,
             model, temperature, max_tokens, created_at, updated_at
      FROM user_chat_model_config
      WHERE user_id = #{userId} AND alias = #{alias}
      """)
  UserChatModelConfig selectByUserAndAlias(@Param("userId") Long userId, @Param("alias") String alias);

  @Select(
      """
      SELECT id, user_id, alias, label, base_url, api_key_cipher, completions_path,
             model, temperature, max_tokens, created_at, updated_at
      FROM user_chat_model_config
      WHERE user_id = #{userId}
      ORDER BY updated_at DESC
      """)
  List<UserChatModelConfig> selectByUserId(@Param("userId") Long userId);

  @Select(
      """
      SELECT id, user_id, alias, label, base_url, api_key_cipher, completions_path,
             model, temperature, max_tokens, created_at, updated_at
      FROM user_chat_model_config
      WHERE id = #{id} AND user_id = #{userId}
      """)
  UserChatModelConfig selectByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

  @Insert(
      """
      INSERT INTO user_chat_model_config
        (user_id, alias, label, base_url, api_key_cipher, completions_path, model, temperature, max_tokens, created_at, updated_at)
      VALUES
        (#{userId}, #{alias}, #{label}, #{baseUrl}, #{apiKeyCipher}, #{completionsPath}, #{model}, #{temperature}, #{maxTokens}, NOW(), NOW())
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id")
  void insert(UserChatModelConfig row);

  @Update(
      """
      UPDATE user_chat_model_config SET
        label = #{label},
        base_url = #{baseUrl},
        api_key_cipher = #{apiKeyCipher},
        completions_path = #{completionsPath},
        model = #{model},
        temperature = #{temperature},
        max_tokens = #{maxTokens},
        updated_at = NOW()
      WHERE id = #{id} AND user_id = #{userId}
      """)
  int update(UserChatModelConfig row);

  @Delete("DELETE FROM user_chat_model_config WHERE id = #{id} AND user_id = #{userId}")
  int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);
}
