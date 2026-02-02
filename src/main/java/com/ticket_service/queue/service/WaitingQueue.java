package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WaitingQueue {

    private final RedisTemplate<String, String> queueRedisTemplate;

    @Value("${queue.waiting-timeout-seconds:1800}")
    private long waitingTimeoutSeconds;

    public boolean contains(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        return queueRedisTemplate.opsForZSet().score(key, userId) != null;
    }

    public void add(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        double score = System.currentTimeMillis();
        queueRedisTemplate.opsForZSet().add(key, userId, score);
        queueRedisTemplate.expire(key, waitingTimeoutSeconds, TimeUnit.SECONDS);
    }

    public void remove(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        queueRedisTemplate.opsForZSet().remove(key, userId);
    }

    public Long rank(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        return queueRedisTemplate.opsForZSet().rank(key, userId);
    }

    public List<String> getAll(Long concertId) {
        String key = QueueKey.waitingQueue(concertId);
        Set<String> users = queueRedisTemplate.opsForZSet().range(key, 0, -1);
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(users);
    }

    public List<String> pollTopUsers(Long concertId, long count) {
        String key = QueueKey.waitingQueue(concertId);
        Set<String> users = queueRedisTemplate.opsForZSet().range(key, 0, count - 1);

        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>(users);
        for (String userId : result) {
            queueRedisTemplate.opsForZSet().remove(key, userId);
        }
        return result;
    }
}
