package com.jianbo.localaiknowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 配置。
 *
 * <p>提供项目级 {@link ObjectMapper}，供 {@code SecurityConfig} / ES 服务等手动注入使用。
 *
 * <p>说明：Spring Boot 4 把 {@code JacksonAutoConfiguration} 从 {@code spring-boot-autoconfigure}
 * 拆到独立的 {@code spring-boot-jackson-autoconfigure} 模块。本工程未传递依赖到该模块，因此不会有自动 Bean，
 * 这里显式提供。{@code @ConditionalOnMissingBean} 保证未来若引入了相关 starter 也不冲突。
 */
@Configuration
public class JacksonConfig {

  @Bean
  @ConditionalOnMissingBean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
