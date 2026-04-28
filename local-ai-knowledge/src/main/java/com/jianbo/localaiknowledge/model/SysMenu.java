package com.jianbo.localaiknowledge.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单实体
 */
@Data
public class SysMenu {
    private Long id;
    private Long parentId;      // 父菜单ID，0为顶级
    private String name;        // 菜单名称
    private String path;        // 路由路径
    private String component;   // 组件路径
    private String icon;        // 图标
    private Integer sortOrder;  // 排序
    private Boolean isVisible;   // 是否显示
    private Boolean isEnabled;   // 是否启用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SysMenu> children;  // 子菜单（树形结构）
}
