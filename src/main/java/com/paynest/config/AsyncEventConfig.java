package com.paynest.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@Slf4j
public class AsyncEventConfig implements AsyncConfigurer {

    public static final String NOTIFICATION_EVENT_EXECUTOR = "notificationEventExecutor";

    @Bean(name = NOTIFICATION_EVENT_EXECUTOR)
    public Executor notificationEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notification-event-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> log.error(
                "Uncaught async exception. method={}, message={}",
                method.getName(),
                throwable.getMessage(),
                throwable
        );
    }
}
