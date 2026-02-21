package com.ticket_service.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${sse.thread-pool.core-size:10}")
    private int corePoolSize;

    @Value("${sse.thread-pool.max-size:50}")
    private int maxPoolSize;

    @Value("${sse.thread-pool.queue-capacity:100}")
    private int queueCapacity;

    @Bean(name = "sseTaskExecutor")
    public Executor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("sse-");
        executor.setWaitForTasksToCompleteOnShutdown(true); // 종료 시 큐에 남은 작업을 완료할 때까지 대기
        executor.setAwaitTerminationSeconds(30); // 최대 30초간 대기 후 강제 종료
        executor.initialize();
        return executor;
    }
}
