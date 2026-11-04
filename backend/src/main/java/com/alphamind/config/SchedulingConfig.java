package com.alphamind.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用 @Scheduled；各任务默认通过空 cron（"-"）关闭，需要显式配置才会运行。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
