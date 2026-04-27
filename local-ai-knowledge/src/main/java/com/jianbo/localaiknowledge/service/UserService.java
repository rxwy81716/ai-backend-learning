package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.model.SysRole;
import com.jianbo.localaiknowledge.model.SysUser;
import com.jianbo.localaiknowledge.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务（注册 / 登录 / 角色管理）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     *
     * @return token + 用户信息
     */
    @Transactional
    public Map<String, Object> register(String username, String password, String nickname) {
        // 1. 检查用户名是否已存在
        if (userMapper.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在: " + username);
        }

        // 2. 创建用户
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        userMapper.insert(user);

        // 3. 分配默认角色（ROLE_USER）
        SysRole defaultRole = userMapper.findRoleByCode("ROLE_USER");
        if (defaultRole != null) {
            userMapper.assignRole(user.getId(), defaultRole.getId());
        }

        log.info("用户注册成功 | username={}, id={}", username, user.getId());

        // 4. 生成 Token
        List<String> roles = List.of("ROLE_USER");
        String token = jwtUtil.generateToken(user.getId(), username, roles);

        return buildLoginResponse(user, roles, token);
    }

    /**
     * 用户登录
     *
     * @return token + 用户信息
     */
    public Map<String, Object> login(String username, String password) {
        // 1. 查找用户
        SysUser user = userMapper.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 2. 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3. 检查是否启用
        if (!user.getEnabled()) {
            throw new IllegalArgumentException("账号已被禁用，请联系管理员");
        }

        // 4. 查询角色
        List<SysRole> roles = userMapper.findRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(SysRole::getCode).toList();

        // 5. 生成 Token
        String token = jwtUtil.generateToken(user.getId(), username, roleCodes);

        log.info("用户登录成功 | username={}, roles={}", username, roleCodes);
        return buildLoginResponse(user, roleCodes, token);
    }

    /**
     * 获取当前用户信息
     */
    public Map<String, Object> getUserInfo(Long userId) {
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<SysRole> roles = userMapper.findRolesByUserId(userId);
        List<String> roleCodes = roles.stream().map(SysRole::getCode).toList();

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("username", user.getUsername());
        info.put("nickname", user.getNickname());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        info.put("avatar", user.getAvatar());
        info.put("roles", roleCodes);
        info.put("roleNames", roles.stream().map(SysRole::getName).toList());
        return info;
    }

    /**
     * 管理员：给用户分配角色
     */
    @Transactional
    public void assignRole(Long userId, String roleCode) {
        SysRole role = userMapper.findRoleByCode(roleCode);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + roleCode);
        }
        SysUser user = userMapper.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        userMapper.assignRole(userId, role.getId());
        log.info("分配角色 | userId={}, role={}", userId, roleCode);
    }

    /**
     * 管理员：查看所有用户
     */
    public List<Map<String, Object>> listAllUsers() {
        return userMapper.findAll().stream().map(u -> {
            List<SysRole> roles = userMapper.findRolesByUserId(u.getId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("nickname", u.getNickname());
            map.put("enabled", u.getEnabled());
            map.put("roles", roles.stream().map(SysRole::getCode).toList());
            map.put("createdAt", u.getCreatedAt());
            return map;
        }).toList();
    }

    /**
     * 管理员：启用/禁用用户
     */
    public void setEnabled(Long userId, boolean enabled) {
        userMapper.updateEnabled(userId, enabled);
        log.info("用户状态变更 | userId={}, enabled={}", userId, enabled);
    }

    // ==================== 私有方法 ====================

    private Map<String, Object> buildLoginResponse(SysUser user, List<String> roles, String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("roles", roles);
        return result;
    }
}
