package com.jianbo.crawler.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * XXL-JOB 分布式任务调度配置
 *
 * 仅当 xxl.job.enabled=true 时生效。
 * 简单场景使用 @Scheduled 即可，分布式/可视化管理场景开启 XXL-JOB。
 *
 * 使用前需部署 xxl-job-admin 调度中心：
 *   - GitHub: https://github.com/xuxueli/xxl-job
 *   - 默认地址: http://localhost:8080/xxl-job-admin
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true")
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9999}")
    private int port;

    @Value("${xxl.job.accessToken:default_token}")
    private String accessToken;

    @Value("${xxl.job.executor.logpath:./logs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        log.info("====== XXL-JOB 执行器初始化 ======");
        log.info("调度中心地址: {}", adminAddresses);
        log.info("执行器名称: {}, 端口: {}", appname, port);

        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAppname(appname);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setAccessToken(accessToken);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);

        return executor;
    }
}
