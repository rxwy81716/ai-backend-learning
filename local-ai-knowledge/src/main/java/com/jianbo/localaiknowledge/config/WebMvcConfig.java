package com.jianbo.localaiknowledge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置（前端开发用）
 *
 * <p>前端 Vite Dev Server 默认跑在 5173 端口， 后端跑在 12116，浏览器同源策略会拦截跨域请求。
 *
 * <p>这里允许前端跨域访问后端接口。
 *
 * <p>注意：使用 allowCredentials(true) 时不能使用通配符 "*"， 必须明确指定允许的源。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns(
            "http://localhost:5173", // 本地开发
            "http://127.0.0.1:5173", // 本地开发（IP形式）
            "http://101.132.182.160:5173", // 测试环境后端地址
            "http://101.132.182.160:12116", // 测试环境后端地址（如果前端也部署在同源）
            "http://localhost:4173", // 预览模式
            "http://127.0.0.1:4173" // 预览模式（IP形式）
            )
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("Content-Disposition")
        .allowCredentials(true)
        .maxAge(3600);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // 根路径重定向到 API 文档或返回简单欢迎信息
    registry.addRedirectViewController("/", "/api/rag/sessions");
  }
}
