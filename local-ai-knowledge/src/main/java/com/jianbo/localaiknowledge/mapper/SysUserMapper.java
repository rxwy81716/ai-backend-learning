package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.SysRole;
import com.jianbo.localaiknowledge.model.SysUser;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/** 用户 Mapper */
@Mapper
public interface SysUserMapper {

  @Select("SELECT * FROM sys_user WHERE username = #{username}")
  SysUser findByUsername(@Param("username") String username);

  @Select("SELECT * FROM sys_user WHERE id = #{id}")
  SysUser findById(@Param("id") Long id);

  @Insert(
      "INSERT INTO sys_user (username, password, nickname, email, phone) "
          + "VALUES (#{username}, #{password}, #{nickname}, #{email}, #{phone})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(SysUser user);

  @Update(
      "UPDATE sys_user SET nickname=#{nickname}, email=#{email}, phone=#{phone}, "
          + "avatar=#{avatar}, updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int update(SysUser user);

  @Update("UPDATE sys_user SET enabled=#{enabled}, updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int updateEnabled(@Param("id") Long id, @Param("enabled") Boolean enabled);

  @Update("UPDATE sys_user SET password=#{password}, updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int updatePassword(@Param("id") Long id, @Param("password") String password);

  /** 查询用户的所有角色 */
  @Select(
      "SELECT r.* FROM sys_role r "
          + "INNER JOIN sys_user_role ur ON r.id = ur.role_id "
          + "WHERE ur.user_id = #{userId}")
  List<SysRole> findRolesByUserId(@Param("userId") Long userId);

  /** 给用户分配角色 */
  @Insert(
      "INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId}) "
          + "ON CONFLICT (user_id, role_id) DO NOTHING")
  int assignRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

  /** 清除用户所有角色 */
  @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
  int clearUserRoles(@Param("userId") Long userId);

  /** 根据角色编码查角色 */
  @Select("SELECT * FROM sys_role WHERE code = #{code}")
  SysRole findRoleByCode(@Param("code") String code);

  /** 查询所有用户（管理员用） */
  @Select("SELECT * FROM sys_user ORDER BY created_at DESC")
  List<SysUser> findAll();

  /** 查询所有用户（带角色信息） */
  @Select("SELECT * FROM sys_user ORDER BY created_at DESC")
  @Results({@Result(property = "roles", column = "id", many = @Many(select = "findRolesByUserId"))})
  List<SysUser> findAllWithRoles();

  /** 统计某角色的用户数量 */
  @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
  int countByRoleId(@Param("roleId") Long roleId);

  /** 批量统计多个角色的用户数量（解决 N+1 查询） */
  @MapKey("roleId")
  @Select(
      "<script>"
          + "SELECT role_id AS roleId, COUNT(*) AS count FROM sys_user_role "
          + "WHERE role_id IN "
          + "<foreach item='id' collection='roleIds' open='(' separator=',' close=')'>"
          + "#{id}"
          + "</foreach>"
          + " GROUP BY role_id"
          + "</script>")
  Map<Long, Map<String, Object>> countByRoleIdsRaw(@Param("roleIds") List<Long> roleIds);

  /** 批量统计多个角色的用户数量（便捷方法） */
  default Map<Long, Integer> countByRoleIds(List<Long> roleIds) {
    Map<Long, Map<String, Object>> raw = countByRoleIdsRaw(roleIds);
    Map<Long, Integer> result = new java.util.HashMap<>();
    if (raw != null) {
      raw.forEach((roleId, row) -> result.put(roleId, ((Number) row.get("count")).intValue()));
    }
    return result;
  }

  /** 检查用户名是否存在 */
  @Select("SELECT COUNT(*) > 0 FROM sys_user WHERE username = #{username}")
  boolean existsByUsername(@Param("username") String username);

  /** 统计用户总数 */
  @Select("SELECT COUNT(*) FROM sys_user")
  int count();

  /** 根据ID删除用户 */
  @Delete("DELETE FROM sys_user WHERE id = #{id}")
  int deleteById(@Param("id") Long id);
}
