package com.jianbo.crawler.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executors;

/**
 * 定时任务线程池配置
 *
 * 默认 @Scheduled 使用单线程，多个爬虫任务会串行排队。
 * 此处配置 4 线程的调度池，允许多个爬虫并行执行。
 */
@Slf4j
@Configuration
public class SchedulerConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        // 4 个调度线程，满足 6 个爬虫并发需求（部分错开不会同时触发）
        registrar.setScheduler(Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r);
            t.setName("crawler-sched-" + t.threadId());
            t.setDaemon(true);
            return t;
        }));
        log.info("定时任务调度线程池已配置：poolSize=4");
    }
}
