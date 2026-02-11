package com.ticket_service.common.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class RedissonLockTemplate {
    private final RedissonClient redissonClient;

    @Value("${redis.lock.wait-time}")
    private Duration waitTime;

    @Value("${redis.lock.lease-time}")
    private Duration leaseTime;

    public void executeWithLock(String key, Runnable action) {
        RLock lock = redissonClient.getLock(key);
        boolean locked = false;

        try {
            locked = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);

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

    public <T> T executeWithLock(String key, Supplier<T> action) {
        RLock lock = redissonClient.getLock(key);
        boolean locked = false;

        try {
            locked = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);

            if (!locked) {
                throw new LockAcquisitionException(key);
            }

            return action.get();
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
