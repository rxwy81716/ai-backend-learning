package com.jianbo.localaiknowledge.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 工具类
 *
 * 职责：生成 Token、解析 Token、提取用户信息
 */
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-hours:24}")
    private int expirationHours;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID
     * @param username 用户名
     * @param roles    角色编码列表（如 ["ROLE_USER", "ROLE_ADMIN"]）
     */
    public String generateToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (long) expirationHours * 3600 * 1000);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token，返回 Claims
     * 如果过期或签名无效会抛异常
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 Token 获取 userId */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /** 从 Token 获取用户名 */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /** 从 Token 获取角色列表 */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parseToken(token).get("roles", List.class);
    }

    /** Token 是否有效（未过期 + 签名正确） */
    public boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
