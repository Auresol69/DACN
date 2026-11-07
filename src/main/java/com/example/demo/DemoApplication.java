package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Điểm khởi động ứng dụng Spring Boot CVRP Optimization Engine.
 *
 * @EnableAsync: kích hoạt xử lý bất đồng bộ cho @Async trong Controller.
 * Thread pool được cấu hình trong AsyncConfig (core=4, max=8, queue=50).
 */
@SpringBootApplication
@EnableAsync
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

}
