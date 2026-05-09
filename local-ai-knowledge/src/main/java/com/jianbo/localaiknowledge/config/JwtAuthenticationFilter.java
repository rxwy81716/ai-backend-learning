package com.jianbo.localaiknowledge.config;

import com.jianbo.localaiknowledge.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器
 *
 * <p>从 Authorization: Bearer <token> 中解析 JWT， 将用户信息写入 SecurityContext，后续接口可直接使用。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String header = request.getHeader("Authorization");
    String token = null;

    if (header != null && header.startsWith("Bearer ")) {
      token = header.substring(7);
    } else {
      // 支持通过 query param 传递 token（用于文件下载等场景）
      token = request.getParameter("token");
    }

    if (token != null && !token.isBlank()) {
      try {
        // 一次解析拿所有字段（原先 isValid + getUserId + getUsername + getRoles 解析了 4 次同一 token）
        Claims claims = jwtUtil.parseToken(token);
        Long userId = Long.valueOf(claims.getSubject());
        String username = claims.get("username", String.class);
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        List<SimpleGrantedAuthority> authorities =
            roles == null ? List.of() : roles.stream().map(SimpleGrantedAuthority::new).toList();

        // 用 userId 作为 principal，方便后续获取
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);
        // 附加详情（存 username 方便日志使用）
        authentication.setDetails(username);

        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (ExpiredJwtException e) {
        // Token 过期是正常业务现象（用户长时间不操作），INFO 级足够；
        // 不设认证信息 → Security 后续返回 401，前端跳登录或走 refresh
        log.info("JWT 已过期 | sub={}, expiredAt={}", e.getClaims().getSubject(), e.getClaims().getExpiration());
      } catch (JwtException e) {
        // 签名失败 / 格式不合法 / 不支持的算法 等：通常意味着伪造 / 篡改 / 试探，必须可见
        log.warn("JWT 验证失败（可能是伪造或试探）| ip={}, ua={}, err={}",
            request.getRemoteAddr(), request.getHeader("User-Agent"), e.getMessage());
      } catch (Exception e) {
        // 兜底：理论上不应到这里
        log.error("JWT 处理异常", e);
      }
    }

    filterChain.doFilter(request, response);
  }
}
