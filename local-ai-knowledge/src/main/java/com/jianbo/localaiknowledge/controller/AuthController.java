package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口
 *
 * <p>公开接口： POST /auth/register 注册 POST /auth/login 登录
 *
 * <p>认证接口： GET /auth/me 获取当前用户信息
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

  private final UserService userService;

  /** 用户注册 */
  @PostMapping("/auth/register")
  public Map<String, Object> register(@RequestBody Map<String, String> body) {
    String username = body.get("username");
    String password = body.get("password");
    String nickname = body.get("nickname");

    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("用户名不能为空");
    }
    if (password == null || password.length() < 6) {
      throw new IllegalArgumentException("密码至少6位");
    }

    return userService.register(username, password, nickname);
  }

  /** 用户登录 */
  @PostMapping("/auth/login")
  public Map<String, Object> login(@RequestBody Map<String, String> body) {
    String username = body.get("username");
    String password = body.get("password");

    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new IllegalArgumentException("用户名和密码不能为空");
    }

    return userService.login(username, password);
  }

  /** 获取当前登录用户信息 */
  @GetMapping("/auth/me")
  public Map<String, Object> me(Authentication authentication) {
    if (authentication == null) {
      throw new IllegalArgumentException("未认证");
    }
    Long userId = (Long) authentication.getPrincipal();
    return userService.getUserInfo(userId);
  }

  /** Token 续期（用当前有效 Token 换取新 Token） */
  @PostMapping("/auth/refresh")
  public Map<String, Object> refresh(Authentication authentication) {
    if (authentication == null) {
      throw new IllegalArgumentException("未认证");
    }
    Long userId = (Long) authentication.getPrincipal();
    return userService.refreshToken(userId);
  }
}
