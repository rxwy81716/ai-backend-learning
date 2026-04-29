package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.mapper.SysMenuMapper;
import com.jianbo.localaiknowledge.mapper.SysRoleMapper;
import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.mapper.SystemPromptMapper;
import com.jianbo.localaiknowledge.model.SysMenu;
import com.jianbo.localaiknowledge.model.SysRole;
import com.jianbo.localaiknowledge.model.SysUser;
import com.jianbo.localaiknowledge.model.SystemPrompt;
import com.jianbo.localaiknowledge.service.MenuService;
import com.jianbo.localaiknowledge.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理接口（管理员专用）
 * 用户管理、角色管理、菜单管理、智能体配置
 */
@RestController
@RequestMapping("/api/admin")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final SystemPromptMapper systemPromptMapper;
    private final MenuService menuService;
    private final PasswordEncoder passwordEncoder;

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public List<SysUser> getUsers() {
        return userMapper.findAllWithRoles();
    }

    @PutMapping("/users/{id}/enabled")
    public void setUserEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        userMapper.updateEnabled(id, enabled);
        log.info("设置用户状态 | userId={}, enabled={}", id, enabled);
    }

    @PostMapping("/users/{userId}/role")
    public void assignRole(@PathVariable Long userId, @RequestParam String roleCode) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        SysRole role = userMapper.findRoleByCode(roleCode);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleCode);
        }
        userMapper.clearUserRoles(userId);
        userMapper.assignRole(userId, role.getId());
        log.info("分配角色 | userId={}, role={}", userId, roleCode);
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    public SysUser createUser(@RequestBody SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (userMapper.findByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        
        // 分配默认角色
        SysRole role = userMapper.findRoleByCode("ROLE_USER");
        if (role != null) {
            userMapper.assignRole(user.getId(), role.getId());
        }
        
        log.info("创建用户 | username={}", user.getUsername());
        return user;
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    public void updateUser(@PathVariable Long id, @RequestBody SysUser user) {
        SysUser existing = userMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setId(id);
        // 不更新密码字段
        user.setPassword(null);
        userMapper.update(user);
        log.info("更新用户 | userId={}", id);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(id)) {
            throw new IllegalArgumentException("不能删除自己");
        }
        SysUser existing = userMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        userMapper.clearUserRoles(id);
        userMapper.deleteById(id);
        log.info("删除用户 | userId={}", id);
    }

    // ==================== 角色管理 ====================

    @GetMapping("/roles")
    public List<SysRole> getRoles() {
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

    @PostMapping("/roles")
    public SysRole createRole(@RequestBody SysRole role) {
        if (role.getName() == null || role.getName().isBlank()) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        if (role.getCode() == null || role.getCode().isBlank()) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (userMapper.findRoleByCode(role.getCode()) != null) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        roleMapper.insert(role);
        log.info("创建角色 | name={}, code={}", role.getName(), role.getCode());
        return role;
    }

    @PutMapping("/roles/{id}")
    public void updateRole(@PathVariable Long id, @RequestBody SysRole role) {
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

    @DeleteMapping("/roles/{id}")
    public void deleteRole(@PathVariable Long id) {
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

    // ==================== 菜单管理 ====================

    @GetMapping("/menus")
    public List<SysMenu> getMenus() {
        return menuService.listAllTree();
    }

    @PostMapping("/menus")
    public SysMenu createMenu(@RequestBody SysMenu menu) {
        if (menu.getName() == null || menu.getName().isBlank()) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        if (menu.getPath() == null || menu.getPath().isBlank()) {
            throw new IllegalArgumentException("菜单路径不能为空");
        }
        return menuService.create(menu);
    }

    @PutMapping("/menus/{id}")
    public void updateMenu(@PathVariable Long id, @RequestBody SysMenu menu) {
        SysMenu existing = menuMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("菜单不存在");
        }
        menu.setId(id);
        menuService.update(menu);
    }

    @DeleteMapping("/menus/{id}")
    public void deleteMenu(@PathVariable Long id) {
        SysMenu existing = menuMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("菜单不存在");
        }
        menuService.delete(id);
    }

    // ==================== 角色菜单绑定 ====================

    @GetMapping("/roles/{roleId}/menus")
    public Map<String, Object> getRoleMenus(@PathVariable Long roleId) {
        SysRole role = roleMapper.findById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<SysMenu> menus = menuService.getMenusByRoleId(roleId);
        List<Long> menuIds = menuService.getMenuIdsByRoleId(roleId);
        return Map.of(
            "role", role,
            "menus", menus,
            "menuIds", menuIds
        );
    }

    @PutMapping("/roles/{roleId}/menus")
    public void updateRoleMenus(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        SysRole role = roleMapper.findById(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<Long> menuIds = body.get("menuIds");
        menuService.updateRoleMenus(roleId, menuIds);
        log.info("更新角色菜单权限 | roleId={}, menuIds={}", roleId, menuIds);
    }

    // ==================== 智能体管理（System Prompt） ====================

    @GetMapping("/agents")
    public List<SystemPrompt> getAgents() {
        return systemPromptMapper.selectAll();
    }

    @GetMapping("/agents/{id}")
    public SystemPrompt getAgent(@PathVariable Long id) {
        SystemPrompt agent = systemPromptMapper.selectById(id);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }
        return agent;
    }

    @GetMapping("/agents/default")
    public SystemPrompt getDefaultAgent() {
        SystemPrompt agent = systemPromptMapper.selectDefault();
        if (agent == null) {
            throw new IllegalArgumentException("未设置默认智能体");
        }
        return agent;
    }

    @PostMapping("/agents")
    public SystemPrompt createAgent(@RequestBody SystemPrompt agent) {
        if (agent.getName() == null || agent.getName().isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (agent.getContent() == null || agent.getContent().isBlank()) {
            throw new IllegalArgumentException("Prompt 内容不能为空");
        }
        if (systemPromptMapper.selectByName(agent.getName()) != null) {
            throw new IllegalArgumentException("名称已存在");
        }
        if (Boolean.TRUE.equals(agent.getIsDefault())) {
            systemPromptMapper.clearDefault();
        }
        systemPromptMapper.insert(agent);
        log.info("创建智能体 | name={}, isDefault={}", agent.getName(), agent.getIsDefault());
        return agent;
    }

    @PutMapping("/agents/{id}")
    public void updateAgent(@PathVariable Long id, @RequestBody SystemPrompt agent) {
        SystemPrompt existing = systemPromptMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("智能体不存在");
        }
        if (agent.getContent() == null || agent.getContent().isBlank()) {
            throw new IllegalArgumentException("Prompt 内容不能为空");
        }
        SystemPrompt byName = systemPromptMapper.selectByName(agent.getName());
        if (byName != null && !byName.getId().equals(id)) {
            throw new IllegalArgumentException("名称已存在");
        }
        if (Boolean.TRUE.equals(agent.getIsDefault())) {
            systemPromptMapper.clearDefault();
        }
        agent.setId(id);
        systemPromptMapper.updateById(agent);
        log.info("更新智能体 | id={}", id);
    }

    @PutMapping("/agents/{id}/default")
    public void setDefaultAgent(@PathVariable Long id) {
        SystemPrompt agent = systemPromptMapper.selectById(id);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }
        systemPromptMapper.clearDefault();
        agent.setIsDefault(true);
        systemPromptMapper.updateById(agent);
        log.info("设为默认智能体 | id={}", id);
    }

    @DeleteMapping("/agents/{id}")
    public void deleteAgent(@PathVariable Long id) {
        SystemPrompt agent = systemPromptMapper.selectById(id);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }
        if (Boolean.TRUE.equals(agent.getIsDefault())) {
            throw new IllegalArgumentException("默认智能体不允许删除");
        }
        systemPromptMapper.deleteById(id);
        log.info("删除智能体 | id={}", id);
    }
}
