package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.SysRoleMapper;
import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.model.SysRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 角色服务
 *
 * <p>包含角色管理及关联业务逻辑，从 AdminController 下沉而来。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RoleService {

  private final SysRoleMapper roleMapper;
  private final SysUserMapper userMapper;

  /** 查询所有角色（带用户数量） */
  public List<SysRole> findAllWithUserCount() {
    List<SysRole> roles = roleMapper.findAll();
    if (!roles.isEmpty()) {
      List<Long> roleIds = roles.stream().map(SysRole::getId).toList();
      Map<Long, Integer> countMap = userMapper.countByRoleIds(roleIds);
      for (SysRole role : roles) {
        role.setUserCount(countMap.getOrDefault(role.getId(), 0));
      }
    }
    return roles;
  }

  /** 创建角色 */
  @Transactional
  public SysRole create(SysRole role) {
    if (role.getName() == null || role.getName().isBlank()) {
      throw new IllegalArgumentException("角色名称不能为空");
    }
    if (role.getCode() == null || role.getCode().isBlank()) {
      throw new IllegalArgumentException("角色编码不能为空");
    }
    if (roleMapper.findByCode(role.getCode()) != null) {
      throw new IllegalArgumentException("角色编码已存在");
    }
    roleMapper.insert(role);
    log.info("创建角色 | name={}, code={}", role.getName(), role.getCode());
    return role;
  }

  /** 更新角色 */
  @Transactional
  public void update(Long id, SysRole role) {
    SysRole existing = roleMapper.findById(id);
    if (existing == null) {
      throw new IllegalArgumentException("角色不存在");
    }
    if ("ROLE_ADMIN".equals(existing.getCode()) || "ROLE_USER".equals(existing.getCode())) {
      throw new IllegalArgumentException("系统角色不允许修改");
    }
    role.setId(id);
    roleMapper.update(role);
    log.info("更新角色 | id={}", id);
  }

  /** 删除角色 */
  @Transactional
  public void delete(Long id) {
    SysRole role = roleMapper.findById(id);
    if (role == null) {
      throw new IllegalArgumentException("角色不存在");
    }
    if ("ROLE_ADMIN".equals(role.getCode()) || "ROLE_USER".equals(role.getCode())) {
      throw new IllegalArgumentException("系统角色不允许删除");
    }
    if (userMapper.countByRoleId(id) > 0) {
      throw new IllegalArgumentException("该角色下有用户，不允许删除");
    }
    roleMapper.deleteById(id);
    log.info("删除角色 | id={}", id);
  }
}
