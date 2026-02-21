package com.ticket_service.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@Profile("performance-test")
@RequiredArgsConstructor
public class QueueDataInitializer implements ApplicationRunner {

    private static final String WAITING_QUEUE_PATTERN = "QUEUE:WAITING:*";
    private static final String PROCESSING_SET_PATTERN = "SET:PROCESSING:*";

    private final RedisTemplate<String, String> queueRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        clearQueueData();
    }

    private void clearQueueData() {
        int deletedCount = 0;

        deletedCount += deleteKeysByPattern(WAITING_QUEUE_PATTERN);
        deletedCount += deleteKeysByPattern(PROCESSING_SET_PATTERN);

        log.info("Queue data initialized. Deleted {} keys.", deletedCount);
    }

    private int deleteKeysByPattern(String pattern) {
        Set<String> keys = queueRedisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            queueRedisTemplate.delete(keys);
            return keys.size();
        }
        return 0;
    }
}
