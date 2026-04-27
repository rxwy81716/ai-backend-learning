package com.jianbo.localaiknowledge.controller;

import com.jianbo.localaiknowledge.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证 & 用户管理接口
 *
 * 公开接口：
 *   POST /auth/register    注册
 *   POST /auth/login       登录
 *
 * 认证接口：
 *   GET  /auth/me           获取当前用户信息
 *
 * 管理员接口：
 *   GET  /api/admin/users          用户列表
 *   PUT  /api/admin/user/{id}/role 分配角色
 *   PUT  /api/admin/user/{id}/enabled 启用/禁用
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // ==================== 公开接口 ====================

    /**
     * 用户注册
     */
    @PostMapping("/auth/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.get("nickname");

        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (password == null || password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少6位"));
        }

        try {
            Map<String, Object> result = userService.register(username, password, nickname);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        try {
            Map<String, Object> result = userService.login(username, password);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 认证接口 ====================

    /**
     * 获取当前登录用户信息
     * 需要 Authorization: Bearer <token>
     */
    @GetMapping("/auth/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未认证"));
        }
        Long userId = (Long) authentication.getPrincipal();
        Map<String, Object> info = userService.getUserInfo(userId);
        return ResponseEntity.ok(info);
    }

    // ==================== 管理员接口 ====================

    /**
     * 获取所有用户（仅管理员）
     */
    @GetMapping("/api/admin/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        return ResponseEntity.ok(userService.listAllUsers());
    }

    /**
     * 给用户分配角色（仅管理员）
     */
    @PutMapping("/api/admin/user/{id}/role")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> assignRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String roleCode = body.get("roleCode");
        if (roleCode == null || roleCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "roleCode 不能为空"));
        }
        try {
            userService.assignRole(id, roleCode);
            return ResponseEntity.ok(Map.of("message", "角色分配成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 启用/禁用用户（仅管理员）
     */
    @PutMapping("/api/admin/user/{id}/enabled")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> setEnabled(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled 不能为空"));
        }
        userService.setEnabled(id, enabled);
        return ResponseEntity.ok(Map.of("message", enabled ? "用户已启用" : "用户已禁用"));
    }
}
