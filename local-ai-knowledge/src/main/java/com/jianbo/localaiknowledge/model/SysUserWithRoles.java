package com.jianbo.localaiknowledge.model;

import lombok.Data;

/**
 * 用户-角色联合查询结果（避免 N+1 查询）
 *
 * <p>用于 {@link com.jianbo.localaiknowledge.mapper.SysUserMapper#findAllWithRoles} JOIN 查询结果映射。
 * 一个用户可能有多个角色，JOIN 后会产生多行记录，需要在 Service 层按用户分组。
 */
@Data
public class SysUserWithRoles {

  // 用户字段
  private Long id;
  private String username;
  private String nickname;
  private String email;
  private String phone;
  private String avatar;
  private Boolean enabled;
  private java.time.LocalDateTime createdAt;
  private java.time.LocalDateTime updatedAt;

  // 角色字段（JOIN 后可能为 NULL）
  private Long roleId;
  private String roleCode;
  private String roleName;
}
