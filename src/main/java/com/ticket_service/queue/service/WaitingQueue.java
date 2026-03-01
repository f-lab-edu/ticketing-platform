package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class WaitingQueue {

    private final RedisTemplate<String, String> queueRedisTemplate;

    @Value("${queue.waiting-timeout}")
    private Duration waitingTimeout;

    @Value("${queue.max-waiting-count:20000}")
    private int maxWaitingCount;

    public boolean contains(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        return queueRedisTemplate.opsForZSet().score(key, userId) != null;
    }

    public long size(Long concertId) {
        String key = QueueKey.waitingQueue(concertId);
        Long size = queueRedisTemplate.opsForZSet().zCard(key);
        return size != null ? size : 0L;
    }

    public boolean hasCapacity(Long concertId) {
        return size(concertId) < maxWaitingCount;
    }

    public void add(Long concertId, String userId) {
        String key = QueueKey.waitingQueue(concertId);
        double score = System.currentTimeMillis();
        queueRedisTemplate.opsForZSet().add(key, userId, score);
        queueRedisTemplate.expire(key, waitingTimeout.toMillis(), TimeUnit.MILLISECONDS);
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

    public String pollTopUser(Long concertId) {
        String key = QueueKey.waitingQueue(concertId);
        Set<String> users = queueRedisTemplate.opsForZSet().range(key, 0, 0);

        if (users == null || users.isEmpty()) {
            return null;
        }

        String userId = users.iterator().next();
        queueRedisTemplate.opsForZSet().remove(key, userId);
        return userId;
    }

    public List<PolledUser> pollTopUsersWithWaitingTime(Long concertId, long count) {
        String key = QueueKey.waitingQueue(concertId);
        Set<TypedTuple<String>> usersWithScores = queueRedisTemplate.opsForZSet().rangeWithScores(key, 0, count - 1);

        if (usersWithScores == null || usersWithScores.isEmpty()) {
            return Collections.emptyList();
        }

        long currentTime = System.currentTimeMillis();
        List<PolledUser> result = new ArrayList<>();

        for (TypedTuple<String> tuple : usersWithScores) {
            String userId = tuple.getValue();
            Double score = tuple.getScore();
            if (userId != null) {
                queueRedisTemplate.opsForZSet().remove(key, userId);
                long waitingTimeMs = score != null ? currentTime - score.longValue() : 0L;
                result.add(new PolledUser(userId, waitingTimeMs));
            }
        }
        return result;
    }

    public PolledUser pollTopUserWithWaitingTime(Long concertId) {
        String key = QueueKey.waitingQueue(concertId);
        Set<TypedTuple<String>> usersWithScores = queueRedisTemplate.opsForZSet().rangeWithScores(key, 0, 0);

        if (usersWithScores == null || usersWithScores.isEmpty()) {
            return null;
        }

        TypedTuple<String> tuple = usersWithScores.iterator().next();
        String userId = tuple.getValue();
        Double score = tuple.getScore();

        if (userId != null) {
            queueRedisTemplate.opsForZSet().remove(key, userId);
            long currentTime = System.currentTimeMillis();
            long waitingTimeMs = score != null ? currentTime - score.longValue() : 0L;
            return new PolledUser(userId, waitingTimeMs);
        }
        return null;
    }
}
