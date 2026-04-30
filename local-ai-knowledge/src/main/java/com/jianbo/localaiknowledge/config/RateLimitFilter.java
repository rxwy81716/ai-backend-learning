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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 请求限流过滤器（基于 Redis 固定窗口 INCR + EXPIRE）。
 *
 * <p>策略：
 *
 * <ul>
 *   <li>匿名用户：按 IP 限流，默认每小时 {@code anonymous-max} 次（防爬虫 / 防刷）
 *   <li>已认证用户：仅对重型 chat 接口（{@link #CHAT_PATHS}）限流——
 *       <ul>
 *         <li>单 userId 每秒 ≤ {@code chat.user-qps}（默认 2）
 *         <li>单 userId 每天 ≤ {@code chat.user-daily}（默认 200）
 *         <li>单 IP 每秒 ≤ {@code chat.ip-qps}（默认 5，同 IP 多账号合并）
 *       </ul>
 *   <li>已认证用户的非 chat 接口：不限流（由 Spring Security 鉴权 + 业务层校验兜底）
 * </ul>
 *
 * <p>仅对 /api/** 接口生效，/auth/** 不限流。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

  private final RedisTemplate<String, Object> redisTemplate;

  private static final String KEY_PREFIX = "rate:anon:";
  private static final String KEY_USER_QPS = "rate:chat:user:qps:";
  private static final String KEY_USER_DAY = "rate:chat:user:day:";
  private static final String KEY_IP_QPS = "rate:chat:ip:qps:";

  /** 需要对已认证用户限流的重型接口前缀（chat 同步 + SSE 流式）。 */
  private static final String[] CHAT_PATHS = {"/api/rag/chat", "/api/rag/chat/stream"};

  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  @Value("${app.rate-limit.anonymous-max:20}")
  private int anonymousMax;

  @Value("${app.rate-limit.window-minutes:60}")
  private int windowMinutes;

  @Value("${app.rate-limit.chat.user-qps:2}")
  private int chatUserQps;

  @Value("${app.rate-limit.chat.user-daily:200}")
  private int chatUserDaily;

  @Value("${app.rate-limit.chat.ip-qps:5}")
  private int chatIpQps;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // 只对 /api/** 限流，/auth/** 不限
    return !path.startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String path = request.getRequestURI();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    // 已认证用户（principal 为 Long userId）：仅对 chat 接口加 QPS + 日配额限流
    // 用 instanceof 模式变量绑定，避免后续强转引发的静态分析 NPE 警告
    if (auth != null && auth.getPrincipal() instanceof Long userId) {
      if (isChatPath(path)) {
        String ip = getClientIp(request);
        if (rejectIfChatQuotaExceeded(userId, ip, response)) {
          return;
        }
      }
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
      writeTooManyRequests(
          response,
          "{\"error\":\"请求过于频繁，请登录后使用或稍后再试\","
              + "\"status\":429,"
              + "\"limit\":"
              + anonymousMax
              + ","
              + "\"windowMinutes\":"
              + windowMinutes
              + "}");
      return;
    }

    // 在响应头里告知剩余次数
    if (count != null) {
      response.setHeader("X-RateLimit-Limit", String.valueOf(anonymousMax));
      response.setHeader(
          "X-RateLimit-Remaining", String.valueOf(Math.max(0, anonymousMax - count)));
    }

    filterChain.doFilter(request, response);
  }

  private static boolean isChatPath(String path) {
    if (path == null) return false;
    for (String p : CHAT_PATHS) {
      if (path.equals(p) || path.startsWith(p + "/")) return true;
    }
    return false;
  }

  /**
   * chat 接口配额检查（已认证用户）。三项中任一超限即拒绝并写 429。
   *
   * <p>注意：先检查 IP（成本最低 / 防刷最有效），再检查用户 QPS / 日配额。每个计数都是 Redis INCR + EXPIRE 固定窗口，简单可靠，
   * 没有 Redisson 连接池的额外开销。
   *
   * @return true 表示已拒绝（response 已写入），调用方应直接 return 不继续 filter chain
   */
  private boolean rejectIfChatQuotaExceeded(Long userId, String ip, HttpServletResponse response)
      throws IOException {
    // 1) 单 IP 每秒
    if (incrAndExceeds(KEY_IP_QPS + ip, 1, TimeUnit.SECONDS, chatIpQps)) {
      log.warn("chat 限流 | ip={} 超过每秒 {} 次", ip, chatIpQps);
      writeTooManyRequests(
          response,
          "{\"error\":\"请求过于频繁，请稍后重试\",\"status\":429,\"scope\":\"ip-qps\",\"limit\":"
              + chatIpQps
              + "}");
      return true;
    }
    // 2) 单 userId 每秒
    if (incrAndExceeds(KEY_USER_QPS + userId, 1, TimeUnit.SECONDS, chatUserQps)) {
      log.warn("chat 限流 | userId={} 超过每秒 {} 次", userId, chatUserQps);
      writeTooManyRequests(
          response,
          "{\"error\":\"请求过于频繁，请稍后重试\",\"status\":429,\"scope\":\"user-qps\",\"limit\":"
              + chatUserQps
              + "}");
      return true;
    }
    // 3) 单 userId 每天
    String day = LocalDate.now().format(DAY_FMT);
    if (incrAndExceeds(KEY_USER_DAY + userId + ":" + day, 1, TimeUnit.DAYS, chatUserDaily)) {
      log.warn("chat 限流 | userId={} 超过每日 {} 次", userId, chatUserDaily);
      writeTooManyRequests(
          response,
          "{\"error\":\"今日额度已用完，请明日再试\",\"status\":429,\"scope\":\"user-daily\",\"limit\":"
              + chatUserDaily
              + "}");
      return true;
    }
    return false;
  }

  /**
   * INCR 一个 key，若是新建则附加 TTL；返回 true 表示当前计数 &gt; max（已超限）。 TTL 仅在 count==1 时设置，避免每次都 EXPIRE 拉长窗口。
   */
  private boolean incrAndExceeds(String key, long ttl, TimeUnit unit, long max) {
    Long count = redisTemplate.opsForValue().increment(key);
    if (count != null && count == 1) {
      redisTemplate.expire(key, ttl, unit);
    }
    return count != null && count > max;
  }

  private void writeTooManyRequests(HttpServletResponse response, String body) throws IOException {
    response.setStatus(429);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(body);
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
