package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.SysMenuMapper;
import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.model.SysMenu;
import com.jianbo.localaiknowledge.service.MenuService;
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
    private final SysMenuMapper menuMapper;
    private final MenuService menuService;

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
            menus = menuMapper.findAll().stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsVisible()))
                    .collect(Collectors.toList());
            log.info("管理员获取菜单 | userId={}, menuCount={}", userId, menus.size());
        } else {
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

        return menuService.buildMenuTree(menus);
    }
}
