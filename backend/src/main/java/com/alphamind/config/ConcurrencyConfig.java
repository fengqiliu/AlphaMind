package com.alphamind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 外部数据源（行情、新闻、正文）并发请求共用的有界线程池。
 * 只用于互不依赖的网络读取；Agent 的 ThreadLocal 上下文不会跨入这些线程。
 */
@Configuration
public class ConcurrencyConfig {

    /**
     * 刻意不注册为 {@link java.util.concurrent.Executor} 类型的 bean：Spring Boot 发现任意 Executor bean
     * 就不再创建默认的 applicationTaskExecutor，而 MVC 异步请求（含 SSE）依赖它。
     */
    public record IoExecutor(ExecutorService executor) implements AutoCloseable {
        /** 容器关闭时由 Spring 推断调用。 */
        @Override
        public void close() {
            executor.shutdown();
        }
    }

    @Bean
    public IoExecutor alphamindIoExecutor(@Value("${alphamind.io.threads:16}") int threads) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "alphamind-io-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new IoExecutor(Executors.newFixedThreadPool(Math.max(2, threads), factory));
    }
}
