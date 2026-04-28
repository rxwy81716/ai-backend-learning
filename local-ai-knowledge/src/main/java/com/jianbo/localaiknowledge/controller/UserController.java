package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.SysMenuMapper;
import com.jianbo.localaiknowledge.mapper.SysRoleMapper;
import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.model.SysMenu;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户相关接口
 * 获取当前用户的菜单权限等
 */
@RestController
@RequestMapping("/api/user")
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;

    /**
     * 获取当前用户的菜单列表
     * - 如果是管理员，返回所有可见菜单
     * - 否则，根据用户角色返回对应的菜单
     * - 返回树形结构
     */
    @GetMapping("/menus")
    public List<SysMenu> getMyMenus() {
        Long userId = SecurityUtil.getCurrentUserId();
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }

        List<SysMenu> menus;
        
        if (SecurityUtil.isAdmin()) {
            // 管理员返回所有可见菜单
            menus = menuMapper.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsVisible()))
                    .collect(Collectors.toList());
            log.info("管理员获取菜单 | userId={}, menuCount={}", userId, menus.size());
        } else {
            // 普通用户：根据角色获取菜单
            var roles = userMapper.findRolesByUserId(userId);
            if (roles.isEmpty()) {
                return List.of();
            }
            
            Set<Long> allowedMenuIds = new HashSet<>();
            for (var role : roles) {
                var roleMenuIds = menuMapper.findMenuIdsByRoleId(role.getId());
                allowedMenuIds.addAll(roleMenuIds);
            }
            
            menus = menuMapper.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsVisible()))
                    .filter(m -> allowedMenuIds.contains(m.getId()))
                    .collect(Collectors.toList());
            log.info("用户获取菜单 | userId={}, roles={}, menuCount={}", userId, roles.stream().map(r -> r.getCode()).toList(), menus.size());
        }

        // 转换为树形结构
        return buildMenuTree(menus);
    }

    /**
     * 构建菜单树形结构
     */
    private List<SysMenu> buildMenuTree(List<SysMenu> flatMenus) {
        if (flatMenus == null || flatMenus.isEmpty()) {
            return List.of();
        }

        Map<Long, List<SysMenu>> childrenMap = flatMenus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() != 0)
                .collect(Collectors.groupingBy(SysMenu::getParentId));

        // 找出顶级菜单（parentId 为 0 或 null）
        return flatMenus.stream()
                .filter(m -> m.getParentId() == null || m.getParentId() == 0L)
                .peek(root -> buildChildren(root, childrenMap))
                .sorted(Comparator.comparing(m -> m.getSortOrder() != null ? m.getSortOrder() : 99))
                .collect(Collectors.toList());
    }

    /**
     * 递归构建子菜单
     */
    private void buildChildren(SysMenu parent, Map<Long, List<SysMenu>> childrenMap) {
        List<SysMenu> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children.stream()
                    .peek(child -> buildChildren(child, childrenMap))
                    .sorted(Comparator.comparing(m -> m.getSortOrder() != null ? m.getSortOrder() : 99))
                    .collect(Collectors.toList()));
        }
    }
}
