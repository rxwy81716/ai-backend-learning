package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.SysMenuMapper;
import com.jianbo.localaiknowledge.mapper.SysRoleMapper;
import com.jianbo.localaiknowledge.model.SysMenu;
import com.jianbo.localaiknowledge.model.SysRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** 菜单服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

  private final SysMenuMapper menuMapper;
  private final SysRoleMapper roleMapper;

  /** 获取所有菜单 */
  public List<SysMenu> listAll() {
    return menuMapper.findAll();
  }

  /** 获取菜单详情 */
  public SysMenu getById(Long id) {
    return menuMapper.findById(id);
  }

  /** 创建菜单 */
  public SysMenu create(SysMenu menu) {
    if (menu.getParentId() == null) menu.setParentId(0L);
    if (menu.getSortOrder() == null) menu.setSortOrder(0);
    if (menu.getIsVisible() == null) menu.setIsVisible(true);
    if (menu.getIsEnabled() == null) menu.setIsEnabled(true);
    menuMapper.insert(menu);
    log.info("创建菜单 | id={}, name={}", menu.getId(), menu.getName());
    return menu;
  }

  /** 更新菜单 */
  public void update(SysMenu menu) {
    menuMapper.update(menu);
    log.info("更新菜单 | id={}", menu.getId());
  }

  /** 删除菜单 */
  public void delete(Long id) {
    menuMapper.deleteById(id);
    log.info("删除菜单 | id={}", id);
  }

  /** 获取角色的菜单列表 */
  public List<SysMenu> getMenusByRoleId(Long roleId) {
    return roleMapper.findMenusByRoleId(roleId);
  }

  /** 获取角色的菜单ID列表 */
  public List<Long> getMenuIdsByRoleId(Long roleId) {
    return menuMapper.findMenuIdsByRoleId(roleId);
  }

  /** 更新角色菜单权限 */
  @Transactional
  public void updateRoleMenus(Long roleId, List<Long> menuIds) {
    menuMapper.deleteByRoleId(roleId);
    if (menuIds != null && !menuIds.isEmpty()) {
      menuMapper.insertRoleMenus(roleId, menuIds);
    }
    log.info("更新角色菜单 | roleId={}, menuIds={}", roleId, menuIds);
  }

  /** 获取所有菜单（树形结构） */
  public List<SysMenu> listAllTree() {
    return buildMenuTree(menuMapper.findAll());
  }

  /** 将扁平菜单列表构建为树形结构 */
  public List<SysMenu> buildMenuTree(List<SysMenu> flatMenus) {
    if (flatMenus == null || flatMenus.isEmpty()) {
      return List.of();
    }

    Map<Long, List<SysMenu>> childrenMap =
        flatMenus.stream()
            .filter(m -> m.getParentId() != null && m.getParentId() != 0)
            .collect(Collectors.groupingBy(SysMenu::getParentId));

    return flatMenus.stream()
        .filter(m -> m.getParentId() == null || m.getParentId() == 0L)
        .peek(root -> buildChildren(root, childrenMap))
        .sorted(Comparator.comparing(m -> m.getSortOrder() != null ? m.getSortOrder() : 99))
        .collect(Collectors.toList());
  }

  private void buildChildren(SysMenu parent, Map<Long, List<SysMenu>> childrenMap) {
    List<SysMenu> children = childrenMap.get(parent.getId());
    if (children != null && !children.isEmpty()) {
      parent.setChildren(
          children.stream()
              .peek(child -> buildChildren(child, childrenMap))
              .sorted(Comparator.comparing(m -> m.getSortOrder() != null ? m.getSortOrder() : 99))
              .collect(Collectors.toList()));
    }
  }
}
