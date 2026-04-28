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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
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
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://101.132.182.160:5173"));
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

            // 路由规则
            .authorizeHttpRequests(auth -> auth
                // /auth/me 需要认证
                .requestMatchers("/auth/me").authenticated()
                // 注册登录公开
                .requestMatchers("/auth/**").permitAll()
                // 用户接口：需要认证
                .requestMatchers("/api/user/**").authenticated()
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

            // 自定义 401 / 403 响应（返回统一 JSON 格式）
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Map.of(
                                    "code", 401,
                                    "message", "未认证，请先登录",
                                    "data", null)));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                            objectMapper.writeValueAsString(Map.of(
                                    "code", 403,
                                    "message", "权限不足",
                                    "data", null)));
                })
            );

        return http.build();
    }
}
