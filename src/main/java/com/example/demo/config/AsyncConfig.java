package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Cấu hình Async Thread Pool cho CVRP Optimization API.
 *
 * GA Engine là CPU-bound task nặng → thread pool được tách biệt khỏi
 * Tomcat I/O threads để không block request handling của server.
 *
 * Tham số:
 *   corePoolSize  = số CPU logic (đảm bảo luôn có thread sẵn sàng)
 *   maxPoolSize   = 2 × corePoolSize (xử lý burst ngắn hạn)
 *   queueCapacity = 50 (buffer request khi pool đầy)
 *   threadNamePrefix = "ga-worker-" (dễ trace trong log/thread dump)
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "gaTaskExecutor")
    public Executor gaTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ga-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
