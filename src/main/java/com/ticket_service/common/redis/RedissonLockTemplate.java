package com.ticket_service.common.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedissonLockTemplate {
    private final RedissonClient redissonClient;

    @Value("${redis.lock.wait-time}")
    private long waitTime;

    @Value("${redis.lock.lease-time}")
    private long leaseTime;

    public void executeWithLock(String key, Runnable action) {
        RLock lock = redissonClient.getLock(key);
        boolean locked = false;

        try {
            locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

            if (!locked) {
                throw new LockAcquisitionException(key);
            }

            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lock interrupted", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
