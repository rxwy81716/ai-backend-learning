package com.jianbo.localaiknowledge.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // 至少 32 字节的 secret（HMAC-SHA256 要求）
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-for-jwt-at-least-32-bytes-long!!");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 86400000L);
    }

    @Test
    @DisplayName("生成 + 解析 token 应还原 userId/username/roles")
    void generateAndParse() {
        String token = jwtUtil.generateToken(42L, "admin", List.of("ROLE_ADMIN", "ROLE_USER"));

        assertThat(jwtUtil.isValid(token)).isTrue();
        assertThat(jwtUtil.getUserId(token)).isEqualTo(42L);
        assertThat(jwtUtil.getUsername(token)).isEqualTo("admin");
        assertThat(jwtUtil.getRoles(token)).containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    @DisplayName("篡改 token → 无效")
    void tamperedToken() {
        String token = jwtUtil.generateToken(1L, "user", List.of("ROLE_USER"));
        String tampered = token + "x";
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("过期 token → 解析抛异常")
    void expiredToken() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L); // 已过期
        String token = jwtUtil.generateToken(1L, "user", List.of());
        assertThat(jwtUtil.isValid(token)).isFalse();
    }
}
