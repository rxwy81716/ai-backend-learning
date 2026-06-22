package com.jianbo.localaiknowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置
 *
 * <p>路由放行规则： /auth/** 注册 / 登录（公开） /api/admin/** 仅 ROLE_ADMIN 其余 /api/** 需要认证（任意角色）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RateLimitFilter rateLimitFilter;
  private final ObjectMapper objectMapper;

  /** 允许跨域的 Origin；逗号分隔。默认含本地开发环境，生产请通过 app.cors.allowed-origins 覆盖。 */
  @Value("${app.cors.allowed-origins:http://localhost:5173}")
  private String allowedOriginsRaw;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    // 从配置读取允许的 Origin，避免硬编码生产 IP
    List<String> origins =
        java.util.Arrays.stream(allowedOriginsRaw.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 启用 CORS
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // 无状态 API，关闭 CSRF 和 Session
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 路由规则（具体路径需放在通配符之前）
        .authorizeHttpRequests(
            auth ->
                auth
                    // 注册登录公开
                    .requestMatchers("/auth/login", "/auth/register")
                    .permitAll()
                    // Round 4：actuator 监控端点，仅 /health 公开，其他需要 ADMIN 角色
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasAuthority("ROLE_ADMIN")
                    // /auth/me 和 /auth/refresh 需要认证
                    .requestMatchers("/auth/me", "/auth/refresh")
                    .authenticated()
                    // 用户接口：需要认证
                    .requestMatchers("/api/user/**")
                    .authenticated()
                    // 管理员接口：必须 ROLE_ADMIN
                    .requestMatchers("/api/admin/**")
                    .hasAuthority("ROLE_ADMIN")
                    // 文档写操作（上传/重解析/删除）必须认证；爬虫专用接口走 X-Crawler-Key（路径 /api/doc/crawler-upload，不在此列表）
                    .requestMatchers("/api/doc/upload", "/api/doc/reparse/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/doc/**")
                    .authenticated()
                    // 其余 /api/**：允许匿名访问（查询类），由 RateLimitFilter 限流
                    .requestMatchers("/api/**")
                    .permitAll()
                    // 其他资源放行
                    .anyRequest()
                    .permitAll())

        // 过滤器顺序：JWT 解析 → 限流判断 → Security 鉴权
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)

        // 自定义 401 / 403 响应（返回统一 JSON 格式）
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                        (request, response, authException) -> {
                          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write(
                                  objectMapper.writeValueAsString(
                                      Map.of("code", 401, "message", "未认证，请先登录", "data", null)));
                        })
                    .accessDeniedHandler(
                        (request, response, accessDeniedException) -> {
                          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          response.setCharacterEncoding("UTF-8");
                          response
                              .getWriter()
                              .write(
                                  objectMapper.writeValueAsString(
                                      Map.of("code", 403, "message", "权限不足", "data", null)));
                        }));

    return http.build();
  }
}
