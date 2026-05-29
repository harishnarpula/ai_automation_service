package com.aiautomationservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

// ─────────────────────────────────────────────────────────────────────────────
// Configures a proper thread pool for @Async methods.
//
// Without this, Spring uses a default SimpleAsyncTaskExecutor that creates a
// NEW THREAD for every single call — no pooling, no queue limit, can exhaust
// memory under load. This gives us a real bounded pool.
//
// Pool sizing for local dev:
//   core=2, max=5, queue=100
// Each webhook reply needs 1 thread for ~3-8 seconds (DB + AI + UltraMsg call).
// ─────────────────────────────────────────────────────────────────────────────
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("radha-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("[AsyncConfig] Thread pool initialized: core=2, max=5, queue=100");
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Log any uncaught exceptions from @Async methods that return void
        return (ex, method, params) ->
                log.error("[Async] Uncaught exception in {}: {}", method.getName(), ex.getMessage(), ex);
    }
}