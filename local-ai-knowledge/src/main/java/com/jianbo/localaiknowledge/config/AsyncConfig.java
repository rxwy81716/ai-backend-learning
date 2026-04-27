package com.jianbo.localaiknowledge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步/调度配置
 *
 * 文档解析已改为 Redisson 队列 + 独立消费者线程池（DocParseQueueConsumer）。
 * 保留此类供 @Scheduled 等场景使用。
 */
@Configuration
@EnableScheduling
public class AsyncConfig {
}
