package com.umurinan.eda.ch09.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Dedicated executor for the OutboxPoller. Keeps blocking Kafka sends off the
     * shared scheduler thread so other @Scheduled tasks are never starved.
     */
    @Bean("outboxExecutor")
    public Executor outboxExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("outbox-poller-");
        executor.initialize();
        return executor;
    }
}
