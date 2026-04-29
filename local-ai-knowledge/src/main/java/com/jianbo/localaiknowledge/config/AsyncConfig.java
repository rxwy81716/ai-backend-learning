package com.jianbo.localaiknowledge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度配置（@Scheduled）。
 *
 * <p>文档解析走 Redisson 队列 + 独立消费者线程池（DocParseQueueConsumer），不依赖 @Async。
 */
@Configuration
@EnableScheduling
public class AsyncConfig {}
