package com.jianbo.springai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置（前端开发用）
 *
 * 前端 Vite Dev Server 默认跑在 5173 端口，
 * 后端跑在 12115，浏览器同源策略会拦截跨域请求。
 *
 * 这里允许所有 /api/** 接口被前端访问。
 *
 * 注意：生产环境应限制 allowedOriginPatterns 为具体域名。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")          // 开发环境放开所有源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
