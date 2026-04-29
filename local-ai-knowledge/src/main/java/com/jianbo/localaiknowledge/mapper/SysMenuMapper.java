package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.SysMenu;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 菜单 Mapper */
@Mapper
public interface SysMenuMapper {

  @Select("SELECT * FROM sys_menu ORDER BY sort_order ASC")
  List<SysMenu> findAll();

  @Select("SELECT * FROM sys_menu WHERE is_visible = TRUE ORDER BY sort_order ASC")
  List<SysMenu> findVisible();

  @Select("SELECT * FROM sys_menu WHERE id = #{id}")
  SysMenu findById(@Param("id") Long id);

  @Insert(
      "INSERT INTO sys_menu (parent_id, name, path, component, icon, sort_order, is_visible, is_enabled) "
          + "VALUES (#{parentId}, #{name}, #{path}, #{component}, #{icon}, #{sortOrder}, #{isVisible}, #{isEnabled})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(SysMenu menu);

  @Update(
      "UPDATE sys_menu SET parent_id=#{parentId}, name=#{name}, path=#{path}, "
          + "component=#{component}, icon=#{icon}, sort_order=#{sortOrder}, "
          + "is_visible=#{isVisible}, is_enabled=#{isEnabled}, updated_at=CURRENT_TIMESTAMP "
          + "WHERE id=#{id}")
  int update(SysMenu menu);

  @Delete("DELETE FROM sys_menu WHERE id = #{id}")
  int deleteById(@Param("id") Long id);

  // ==================== 角色菜单关联 ====================

  @Select(
      """
        SELECT m.* FROM sys_menu m
        INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
        INNER JOIN sys_role r ON rm.role_id = r.id
        WHERE r.code = #{roleCode} AND m.is_visible = TRUE
        ORDER BY m.sort_order ASC
    """)
  List<SysMenu> findByRoleCode(@Param("roleCode") String roleCode);

  @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
  List<Long> findMenuIdsByRoleId(@Param("roleId") Long roleId);

  @Insert(
      "<script>"
          + "INSERT INTO sys_role_menu (role_id, menu_id) VALUES "
          + "<foreach collection='menuIds' item='menuId' separator=',' >"
          + "(#{roleId}, #{menuId})"
          + "</foreach>"
          + " ON CONFLICT DO NOTHING"
          + "</script>")
  void insertRoleMenus(@Param("roleId") Long roleId, @Param("menuIds") List<Long> menuIds);

  @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
  void deleteByRoleId(@Param("roleId") Long roleId);
}
