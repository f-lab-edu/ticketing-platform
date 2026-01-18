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

    /**
     * 대기열에 사용자 등록
     * 분산 락으로 동일 사용자의 중복 등록 방지 (check-then-act race condition)
     */
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

    /** 대기열 내 현재 순번 조회 (0부터 시작, null이면 대기열에 없음) */
    @Override
    public Long getPosition(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        return queueRedisTemplate.opsForZSet().rank(waitingKey, userId);
    }

    /** 입장 가능 여부 확인 (이미 처리중이거나, 대기 순번이 가용 슬롯 내인 경우 true) */
    @Override
    public boolean canEnter(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        String processingKey = QueueKey.processingQueue(ticketStockId);

        Boolean isProcessing = queueRedisTemplate.opsForSet().isMember(processingKey, userId);
        if (Boolean.TRUE.equals(isProcessing)) {
            return true;
        }

        // 현재 처리중인 인원 수 조회
        Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);
        if (processingCount == null) {
            processingCount = 0L;
        }

        // 대기열에서의 순번 조회 (0부터 시작)
        Long rank = queueRedisTemplate.opsForZSet().rank(waitingKey, userId);
        if (rank == null) {
            return false;
        }

        // 입장 가능: 내 순번이 남은 슬롯 수보다 작으면 입장 가능
        // 예: 최대 100명, 현재 95명 처리중 → 슬롯 5개 → rank 0~4만 입장 가능
        long availableSlots = maxProcessingCount - processingCount;
        return rank < availableSlots;
    }

    /**
     * 대기열에서 처리열로 이동 (입장)
     * 분산 락으로 동일 사용자의 동시 입장으로 인한 이중 구매 방지
     */
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

    /** 처리 완료 후 처리열에서 제거 (구매 성공/실패 시 호출) */
    @Override
    public void complete(Long ticketStockId, String userId) {
        String processingKey = QueueKey.processingQueue(ticketStockId);
        queueRedisTemplate.opsForSet().remove(processingKey, userId);
    }

    /** 대기열과 처리열 모두에서 사용자 제거 (취소 시 호출) */
    @Override
    public void dequeue(Long ticketStockId, String userId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        String processingKey = QueueKey.processingQueue(ticketStockId);

        queueRedisTemplate.opsForZSet().remove(waitingKey, userId);
        queueRedisTemplate.opsForSet().remove(processingKey, userId);
    }

    /** 대기열 등록 후 대기열 정보 반환 (최초 등록 시 사용) */
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

    /** 현재 대기열 정보 조회 (폴링 시 사용) */
    @Override
    public QueueInfo getQueueInfo(Long ticketStockId, String userId) {
        Long position = getPosition(ticketStockId, userId);
        boolean canEnter = canEnter(ticketStockId, userId);
        QueueStatus status = determineStatus(position, canEnter);

        return QueueInfo.builder()
                .userId(userId)
                .ticketStockId(ticketStockId)
                .position(position)
                .canEnter(canEnter)
                .status(status)
                .build();
    }

    /** 대기열 순번과 입장 가능 여부로 상태 결정 */
    private QueueStatus determineStatus(Long position, boolean canEnter) {
        if (position == null && canEnter) {
            return QueueStatus.PROCESSING;
        } else if (position == null) {
            return QueueStatus.NOT_IN_QUEUE;
        } else if (canEnter) {
            return QueueStatus.CAN_ENTER;
        } else {
            return QueueStatus.WAITING;
        }
    }
}
