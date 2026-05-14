package com.jianbo.localaiknowledge.service;

import com.jianbo.localaiknowledge.mapper.SysUserMapper;
import com.jianbo.localaiknowledge.model.SysRole;
import com.jianbo.localaiknowledge.model.SysUser;
import com.jianbo.localaiknowledge.model.SysUserWithRoles;
import com.jianbo.localaiknowledge.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 用户服务（注册 / 登录 / 角色管理） */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

  private final SysUserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final StringRedisTemplate redisTemplate;

  /** Redis Key 前缀 */
  private static final String FAILED_KEY_PREFIX = "account:failed:";
  private static final String LOCK_KEY_PREFIX = "account:lock:";

  /** 登录失败限制配置 */
  @Value("${app.security.max-failed-attempts:5}")
  private int maxFailedAttempts;

  @Value("${app.security.lock-duration-minutes:30}")
  private int lockDurationMinutes;

  /**
   * 检查账户是否被锁定
   */
  private boolean isAccountLocked(String username) {
    String lockKey = LOCK_KEY_PREFIX + username;
    String lockValue = redisTemplate.opsForValue().get(lockKey);
    if (lockValue == null) {
      return false;
    }
    long lockUntilMs = Long.parseLong(lockValue);
    if (System.currentTimeMillis() > lockUntilMs) {
      // 锁定已过期，清理
      redisTemplate.delete(lockKey);
      redisTemplate.delete(FAILED_KEY_PREFIX + username);
      return false;
    }
    return true;
  }

  /**
   * 获取锁定剩余时间（分钟）
   */
  private long getLockRemainingMinutes(String username) {
    String lockKey = LOCK_KEY_PREFIX + username;
    String lockValue = redisTemplate.opsForValue().get(lockKey);
    if (lockValue == null) {
      return 0;
    }
    long lockUntilMs = Long.parseLong(lockValue);
    return Math.max(1, (lockUntilMs - System.currentTimeMillis()) / 60000 + 1);
  }

  /**
   * 记录登录失败
   */
  private void recordFailedAttempt(String username) {
    String failedKey = FAILED_KEY_PREFIX + username;
    Long count = redisTemplate.opsForValue().increment(failedKey);
    if (count == null) {
      count = 1L;
    }
    // 设置过期时间（防止数据残留）
    redisTemplate.expire(failedKey, lockDurationMinutes * 2L, TimeUnit.MINUTES);

    if (count >= maxFailedAttempts) {
      long lockUntilMs = System.currentTimeMillis() + lockDurationMinutes * 60 * 1000L;
      String lockKey = LOCK_KEY_PREFIX + username;
      redisTemplate.opsForValue().set(lockKey, String.valueOf(lockUntilMs));
      redisTemplate.delete(failedKey); // 锁定后清除失败次数
      log.warn("账户已被锁定 | username={}, failedAttempts={}, lockUntil={}min后",
          username, count, lockDurationMinutes);
    } else {
      log.debug("登录失败 | username={}, failedAttempts={}/{}", username, count, maxFailedAttempts);
    }
  }

  /**
   * 清除登录失败记录（登录成功时调用）
   */
  private void clearFailedAttempts(String username) {
    redisTemplate.delete(FAILED_KEY_PREFIX + username);
    redisTemplate.delete(LOCK_KEY_PREFIX + username);
  }

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
    user.setNickname(nickname != null && !nickname.isBlank() ? nickname.trim() : username);
    userMapper.insert(user);

    // 3. 分配普通用户角色
    String roleCode = "ROLE_USER";
    SysRole role = userMapper.findRoleByCode(roleCode);
    if (role != null) {
      userMapper.assignRole(user.getId(), role.getId());
    }

    log.info("用户注册成功 | username={}, id={}, roles=[{}]", username, user.getId(), roleCode);

    // 5. 生成 Token
    List<String> roles = List.of(roleCode);
    String token = jwtUtil.generateToken(user.getId(), username, roles);

    return buildLoginResponse(user, roles, token);
  }

  /**
   * 用户登录（带登录失败限制）
   *
   * @return token + 用户信息
   */
  public Map<String, Object> login(String username, String password) {
    // 0. 检查账户是否被锁定
    if (isAccountLocked(username)) {
      long remainingMinutes = getLockRemainingMinutes(username);
      throw new IllegalArgumentException("账户已锁定，请" + remainingMinutes + "分钟后重试");
    }

    // 1. 查找用户
    SysUser user = userMapper.findByUsername(username);
    if (user == null) {
      throw new IllegalArgumentException("用户名或密码错误");
    }

    // 2. 校验密码
    if (!passwordEncoder.matches(password, user.getPassword())) {
      recordFailedAttempt(username);
      throw new IllegalArgumentException("用户名或密码错误");
    }

    // 3. 检查是否启用
    if (!user.getEnabled()) {
      throw new IllegalArgumentException("账号已被禁用，请联系管理员");
    }

    // 4. 登录成功，清除失败记录
    clearFailedAttempts(username);

    // 5. 查询角色
    List<SysRole> roles = userMapper.findRolesByUserId(user.getId());
    List<String> roleCodes = roles.stream().map(SysRole::getCode).toList();

    // 6. 生成 Token
    String token = jwtUtil.generateToken(user.getId(), username, roleCodes);

    log.info("用户登录成功 | username={}, roles={}", username, roleCodes);
    return buildLoginResponse(user, roleCodes, token);
  }

  /** 获取当前用户信息 */
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

  /** 管理员：给用户分配角色 */
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

  /** 管理员：查看所有用户 - 使用 JOIN 避免 N+1 查询 */
  public List<Map<String, Object>> listAllUsers() {
    // JOIN 查询一次性获取所有用户及角色信息，按用户分组后组装
    Map<Long, Map<String, Object>> userMap = new LinkedHashMap<>();
    for (SysUserWithRoles row : userMapper.findAllWithRoles()) {
      Long userId = row.getId();
      // 新用户首次出现，初始化
      if (!userMap.containsKey(userId)) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", row.getId());
        map.put("username", row.getUsername());
        map.put("nickname", row.getNickname());
        map.put("enabled", row.getEnabled());
        map.put("roles", new java.util.ArrayList<String>());
        map.put("createdAt", row.getCreatedAt());
        userMap.put(userId, map);
      }
      // 角色非空时添加（LEFT JOIN 可能有空角色）
      if (row.getRoleId() != null) {
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) userMap.get(userId).get("roles");
        roles.add(row.getRoleCode());
      }
    }
    return new java.util.ArrayList<>(userMap.values());
  }

  /** 管理员：启用/禁用用户 */
  public void setEnabled(Long userId, boolean enabled) {
    userMapper.updateEnabled(userId, enabled);
    log.info("用户状态变更 | userId={}, enabled={}", userId, enabled);
  }

  /** Token 续期：根据当前 userId 签发新 Token */
  public Map<String, Object> refreshToken(Long userId) {
    SysUser user = userMapper.findById(userId);
    if (user == null) {
      throw new IllegalArgumentException("用户不存在");
    }
    if (!user.getEnabled()) {
      throw new IllegalArgumentException("账号已被禁用");
    }
    List<SysRole> roles = userMapper.findRolesByUserId(userId);
    List<String> roleCodes = roles.stream().map(SysRole::getCode).toList();
    String token = jwtUtil.generateToken(userId, user.getUsername(), roleCodes);
    return buildLoginResponse(user, roleCodes, token);
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
