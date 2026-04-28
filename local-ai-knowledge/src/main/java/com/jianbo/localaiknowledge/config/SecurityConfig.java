package com.jianbo.localaiknowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.Map;

/**
 * Spring Security 配置
 *
 * 路由放行规则：
 *   /auth/**              注册 / 登录（公开）
 *   /api/admin/**         仅 ROLE_ADMIN
 *   其余 /api/**          需要认证（任意角色）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 无状态 API，关闭 CSRF 和 Session
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 路由规则
            .authorizeHttpRequests(auth -> auth
                // /auth/me 需要认证
                .requestMatchers("/auth/me").authenticated()
                // 注册登录公开
                .requestMatchers("/auth/**").permitAll()
                // 管理员接口：必须 ROLE_ADMIN
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                // 其余 /api/**：允许匿名访问，由 RateLimitFilter 限流未认证请求
                .requestMatchers("/api/**").permitAll()
                // 其他资源放行
                .anyRequest().permitAll()
            )

            // 过滤器顺序：JWT 解析 → 限流判断 → Security 鉴权
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)

            // 自定义 401 / 403 响应（返回 JSON 而非默认 HTML）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Map.of(
                                    "error", "未认证，请先登录",
                                    "status", 401)));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Map.of(
                                    "error", "权限不足",
                                    "status", 403)));
                })
            );

        return http.build();
    }
}
