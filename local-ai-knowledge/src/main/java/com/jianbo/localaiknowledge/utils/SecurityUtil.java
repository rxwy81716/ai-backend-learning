package com.jianbo.localaiknowledge.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 安全上下文工具类
 *
 * <p>从 SecurityContext 中提取当前登录用户信息 （由 JwtAuthenticationFilter 写入）
 */
public final class SecurityUtil {

  private SecurityUtil() {}

  /**
   * 获取当前登录用户 ID
   *
   * @return userId，未认证返回 null
   */
  public static Long getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      return userId;
    }
    return null;
  }

  /** 获取当前登录用户 ID（字符串形式，方便传参） */
  public static String getCurrentUserIdStr() {
    Long id = getCurrentUserId();
    return id != null ? String.valueOf(id) : null;
  }

  /** 获取当前用户名 */
  public static String getCurrentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getDetails() instanceof String username) {
      return username;
    }
    return null;
  }

  /** 获取当前用户角色列表 */
  public static List<String> getCurrentRoles() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null) {
      return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
    return List.of();
  }

  /** 当前用户是否有指定角色 */
  public static boolean hasRole(String role) {
    return getCurrentRoles().contains(role);
  }

  /** 当前用户是否为管理员 */
  public static boolean isAdmin() {
    return hasRole("ROLE_ADMIN");
  }
}
