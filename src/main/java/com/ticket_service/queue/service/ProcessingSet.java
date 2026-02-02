package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ProcessingSet {

    private final RedisTemplate<String, String> queueRedisTemplate;

    @Value("${queue.entry-timeout-seconds:300}")
    private long entryTimeoutSeconds;

    @Value("${queue.max-processing-count:100}")
    private int maxProcessingCount;

    public boolean contains(Long concertId, String userId) {
        String key = QueueKey.processingSet(concertId);
        return Boolean.TRUE.equals(queueRedisTemplate.opsForSet().isMember(key, userId));
    }

    public void add(Long concertId, String userId) {
        String key = QueueKey.processingSet(concertId);
        queueRedisTemplate.opsForSet().add(key, userId);
        queueRedisTemplate.expire(key, entryTimeoutSeconds, TimeUnit.SECONDS);
    }

    public void addAll(Long concertId, List<String> userIds) {
        String key = QueueKey.processingSet(concertId);
        for (String userId : userIds) {
            queueRedisTemplate.opsForSet().add(key, userId);
        }
        queueRedisTemplate.expire(key, entryTimeoutSeconds, TimeUnit.SECONDS);
    }

    public void remove(Long concertId, String userId) {
        String key = QueueKey.processingSet(concertId);
        queueRedisTemplate.opsForSet().remove(key, userId);
    }

    public long size(Long concertId) {
        String key = QueueKey.processingSet(concertId);
        Long size = queueRedisTemplate.opsForSet().size(key);
        return size != null ? size : 0L;
    }

    public boolean hasCapacity(Long concertId) {
        return size(concertId) < maxProcessingCount;
    }

    public long remainingCapacity(Long concertId) {
        return maxProcessingCount - size(concertId);
    }
}
