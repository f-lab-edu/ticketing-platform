package com.ticket_service.queue.service;

import com.ticket_service.common.redis.LockKey;
import com.ticket_service.common.redis.QueueKey;
import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.domain.QueueInfo;
import com.ticket_service.queue.domain.QueueStatus;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 대기열 서비스
 * - WAITING: Sorted Set (score = timestamp, 선착순 보장)
 * - PROCESSING: Set (동시 처리중인 사용자)
 */
@Service
@RequiredArgsConstructor
public class RedisQueueService implements QueueService {

    private final RedisTemplate<String, String> queueRedisTemplate;
    private final RedissonLockTemplate redissonLockTemplate;

    @Value("${queue.max-processing-count:100}")
    private int maxProcessingCount;

    @Value("${queue.entry-timeout-seconds:300}")
    private long entryTimeoutSeconds;

    @Value("${queue.waiting-timeout-seconds:1800}")
    private long waitingTimeoutSeconds;

    // 분산 락: 동일 사용자의 중복 등록 방지 (check-then-act race condition)
    @Override
    public Long enqueue(Long ticketStockId, String userId) {
        return redissonLockTemplate.executeWithLock(
                LockKey.queueUser(ticketStockId, userId),
                () -> {
                    String waitingKey = QueueKey.waitingQueue(ticketStockId);
                    String processingKey = QueueKey.processingQueue(ticketStockId);

                    Boolean isProcessing = queueRedisTemplate.opsForSet().isMember(processingKey, userId);
                    if (Boolean.TRUE.equals(isProcessing)) {
                        throw new AlreadyInQueueException("이미 입장한 사용자입니다.");
                    }

                    Double existingScore = queueRedisTemplate.opsForZSet().score(waitingKey, userId);
                    if (existingScore != null) {
                        throw new AlreadyInQueueException("이미 대기열에 등록된 사용자입니다.");
                    }

                    double score = System.currentTimeMillis();
                    queueRedisTemplate.opsForZSet().add(waitingKey, userId, score);
                    queueRedisTemplate.expire(waitingKey, waitingTimeoutSeconds, TimeUnit.SECONDS);

                    return queueRedisTemplate.opsForZSet().rank(waitingKey, userId);
                }
        );
    }

    @Override
    public Long getPosition(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        return queueRedisTemplate.opsForZSet().rank(waitingKey, userId);
    }

    @Override
    public boolean canEnter(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        String processingKey = QueueKey.processingQueue(ticketStockId);

        Boolean isProcessing = queueRedisTemplate.opsForSet().isMember(processingKey, userId);
        if (Boolean.TRUE.equals(isProcessing)) {
            return true;
        }

        Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);
        if (processingCount == null) {
            processingCount = 0L;
        }

        Long rank = queueRedisTemplate.opsForZSet().rank(waitingKey, userId);
        if (rank == null) {
            return false;
        }

        long availableSlots = maxProcessingCount - processingCount;
        return rank < availableSlots;
    }

    // 분산 락: 동일 사용자의 동시 입장으로 인한 이중 구매 방지
    @Override
    public void enter(Long ticketStockId, String userId) {
        redissonLockTemplate.executeWithLock(
                LockKey.queueUser(ticketStockId, userId),
                () -> {
                    String waitingKey = QueueKey.waitingQueue(ticketStockId);
                    String processingKey = QueueKey.processingQueue(ticketStockId);

                    queueRedisTemplate.opsForZSet().remove(waitingKey, userId);
                    queueRedisTemplate.opsForSet().add(processingKey, userId);
                    queueRedisTemplate.expire(processingKey, entryTimeoutSeconds, TimeUnit.SECONDS);
                }
        );
    }

    @Override
    public void complete(Long ticketStockId, String userId) {
        String processingKey = QueueKey.processingQueue(ticketStockId);
        queueRedisTemplate.opsForSet().remove(processingKey, userId);
    }

    @Override
    public void dequeue(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        String processingKey = QueueKey.processingQueue(ticketStockId);

        queueRedisTemplate.opsForZSet().remove(waitingKey, userId);
        queueRedisTemplate.opsForSet().remove(processingKey, userId);
    }

    @Override
    public QueueInfo registerAndGetInfo(Long ticketStockId, String userId) {
        Long position = enqueue(ticketStockId, userId);
        boolean canEnter = canEnter(ticketStockId, userId);
        QueueStatus status = canEnter ? QueueStatus.CAN_ENTER : QueueStatus.WAITING;

        return QueueInfo.builder()
                .userId(userId)
                .ticketStockId(ticketStockId)
                .position(position)
                .canEnter(canEnter)
                .status(status)
                .build();
    }

    @Override
    public QueueInfo getQueueInfo(Long ticketStockId, String userId) {
        Long position = getPosition(ticketStockId, userId);
        boolean canEnter = canEnter(ticketStockId, userId);
        QueueStatus status = QueueStatus.determine(position, canEnter);

        return QueueInfo.builder()
                .userId(userId)
                .ticketStockId(ticketStockId)
                .position(position)
                .canEnter(canEnter)
                .status(status)
                .build();
    }
}
