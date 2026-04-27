package com.alphamind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * AlphaMind 后端启动类
 */
@SpringBootApplication
@EnableConfigurationProperties
public class AlphaMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlphaMindApplication.class, args);
    }
}
