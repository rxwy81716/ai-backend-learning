package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 系统用户 */
@Data
public class SysUser {

  private Long id;
  private String username;
  private String password;
  private String nickname;
  private String email;
  private String phone;
  private String avatar;
  private Boolean enabled;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 用户拥有的角色（查询时关联填充） */
  private List<SysRole> roles;
}
