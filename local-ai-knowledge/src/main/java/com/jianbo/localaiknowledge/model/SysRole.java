package com.jianbo.localaiknowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统角色
 */
@Data
public class SysRole {

    private Long id;
    /** 角色编码：ROLE_USER / ROLE_VIP / ROLE_ADMIN */
    private String code;
    /** 角色名称：普通用户 / 会员 / 管理员 */
    private String name;
    private String description;
    private LocalDateTime createdAt;
    /** 关联用户数量（临时字段，不存储） */
    private Integer userCount;
}
