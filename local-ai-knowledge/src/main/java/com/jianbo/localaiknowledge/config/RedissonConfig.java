package com.jianbo.localaiknowledge.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

  @Value("${spring.data.redis.host:127.0.0.1}")
  private String redisHost;

  @Value("${spring.data.redis.port:6379}")
  private int redisPort;

  /** Redis 密码（默认空）。Redis 服务端开启 requirepass 时必须设置，否则 NOAUTH 报错。 */
  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Bean
  @ConditionalOnMissingBean(RedissonClient.class)
  public RedissonClient redissonClient() {
    Config config = new Config();
    config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
    if (redisPassword != null && !redisPassword.isBlank()) {
      config.useSingleServer().setPassword(redisPassword);
    }
    return Redisson.create(config);
  }
}
