package com.jianbo.localaiknowledge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 匿名请求限流过滤器（基于 Redis 滑动窗口）
 *
 * 策略：
 *   - 未携带 Token（匿名用户）：按 IP 限流，默认每小时 20 次
 *   - 已认证用户：不限流（由后续 Sentinel 等更细粒度控制）
 *
 * 仅对 /api/** 接口生效，/auth/** 不限流
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "rate:anon:";

    @Value("${app.rate-limit.anonymous-max:20}")
    private int anonymousMax;

    @Value("${app.rate-limit.window-minutes:60}")
    private int windowMinutes;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 只对 /api/** 限流，/auth/** 不限
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 已认证用户不限流
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long) {
            filterChain.doFilter(request, response);
            return;
        }

        // 匿名用户：按 IP 限流
        String ip = getClientIp(request);
        String key = KEY_PREFIX + ip;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次访问，设置过期时间
            redisTemplate.expire(key, windowMinutes, TimeUnit.MINUTES);
        }

        if (count != null && count > anonymousMax) {
            log.warn("匿名限流触发 | ip={}, count={}, limit={}", ip, count, anonymousMax);
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"请求过于频繁，请登录后使用或稍后再试\"," +
                    "\"status\":429," +
                    "\"limit\":" + anonymousMax + "," +
                    "\"windowMinutes\":" + windowMinutes + "}");
            return;
        }

        // 在响应头里告知剩余次数
        if (count != null) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(anonymousMax));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, anonymousMax - count)));
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // 取第一个（真实客户端 IP）
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
