package com.jianbo.localaiknowledge.mapper;

import com.jianbo.localaiknowledge.model.SysMenu;
import com.jianbo.localaiknowledge.model.SysRole;
import org.apache.ibatis.annotations.*;

import java.util.List;

/** 角色 Mapper */
@Mapper
public interface SysRoleMapper {

  @Select("SELECT * FROM sys_role ORDER BY id ASC")
  List<SysRole> findAll();

  @Select("SELECT * FROM sys_role WHERE id = #{id}")
  SysRole findById(@Param("id") Long id);

  @Select("SELECT * FROM sys_role WHERE code = #{code}")
  SysRole findByCode(@Param("code") String code);

  @Insert(
      "INSERT INTO sys_role (code, name, description) VALUES (#{code}, #{name}, #{description})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(SysRole role);

  @Update("UPDATE sys_role SET name=#{name}, description=#{description} WHERE id=#{id}")
  int update(SysRole role);

  @Delete("DELETE FROM sys_role WHERE id = #{id}")
  int deleteById(@Param("id") Long id);

  /** 查询角色关联的菜单 */
  @Select(
      """
        SELECT m.* FROM sys_menu m
        INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
        WHERE rm.role_id = #{roleId}
        ORDER BY m.sort_order ASC
    """)
  List<SysMenu> findMenusByRoleId(@Param("roleId") Long roleId);
}
