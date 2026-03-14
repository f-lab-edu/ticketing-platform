package com.ticket_service.queue.service;

import com.ticket_service.common.metrics.QueueMetrics;
import com.ticket_service.common.redis.LockKey;
import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import com.ticket_service.queue.exception.QueueFullException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisQueueService implements QueueService {

    private final WaitingQueue waitingQueue;
    private final ProcessingCounter processingCounter;
    private final RedissonLockTemplate redissonLockTemplate;
    private final QueueMetrics queueMetrics;

    @Override
    public Long enterWaitingQueue(Long concertId, String userId) {
        return redissonLockTemplate.executeWithLock(
                LockKey.queueUser(concertId, userId),
                () -> {
                    validateEntry(concertId, userId);
                    waitingQueue.add(concertId, userId);
                    queueMetrics.incrementQueueEnter(concertId);
                    return getPosition(concertId, userId);
                }
        );
    }

    @Override
    public void completeProcessing(Long concertId, String userId) {
        processingCounter.decrement(concertId);
    }

    @Override
    public void removeFromQueue(Long concertId, String userId) {
        waitingQueue.remove(concertId, userId);
    }

    @Override
    public boolean isInProcessing(Long concertId, String userId) {
        // JWT 토큰 기반 인가로 대체되어 더 이상 개별 사용자 추적 불필요
        // 호환성을 위해 false 반환
        return false;
    }

    @Override
    public List<String> permitProcessing(Long concertId) {
        return redissonLockTemplate.executeWithLock(LockKey.queueEnter(concertId), () -> moveToProcessing(concertId));
    }

    @Override
    public String permitOneProcessing(Long concertId) {
        return redissonLockTemplate.executeWithLock(LockKey.queueEnter(concertId), () -> moveOneToProcessing(concertId));
    }

    @Override
    public boolean hasProcessingCapacity(Long concertId) {
        return processingCounter.remainingCapacity(concertId) > 0;
    }

    @Override
    public List<String> getWaitingUsers(Long concertId) {
        return waitingQueue.getAll(concertId);
    }

    public Long getPosition(Long concertId, String userId) {
        return waitingQueue.rank(concertId, userId);
    }

    private List<String> moveToProcessing(Long concertId) {
        int limit = processingCounter.remainingCapacity(concertId);
        if (limit <= 0) {
            return List.of();
        }

        List<PolledUser> polledUsers = waitingQueue.pollTopUsersWithWaitingTime(concertId, limit);
        if (polledUsers.isEmpty()) {
            return List.of();
        }

        // 분산락이 보장된 상태이므로 일괄 증가
        processingCounter.incrementBy(concertId, polledUsers.size());

        List<String> enteredUsers = new ArrayList<>();
        for (PolledUser polledUser : polledUsers) {
            enteredUsers.add(polledUser.userId());
            queueMetrics.recordWaitingTime(concertId, polledUser.waitingTimeMs());
            queueMetrics.incrementProcessingEntered(concertId);
        }

        return enteredUsers;
    }

    private String moveOneToProcessing(Long concertId) {
        if (processingCounter.remainingCapacity(concertId) <= 0) {
            return null;
        }

        PolledUser polledUser = waitingQueue.pollTopUserWithWaitingTime(concertId);
        if (polledUser != null) {
            if (processingCounter.tryIncrement(concertId)) {
                queueMetrics.recordWaitingTime(concertId, polledUser.waitingTimeMs());
                queueMetrics.incrementProcessingEntered(concertId);
                return polledUser.userId();
            } else {
                // 카운터 증가 실패 시 대기열에 다시 추가
                waitingQueue.add(concertId, polledUser.userId());
            }
        }
        return null;
    }

    private void validateEntry(Long concertId, String userId) {
        if (waitingQueue.contains(concertId, userId)) {
            queueMetrics.incrementQueueRejected(concertId, "duplicate");
            throw new AlreadyInQueueException("이미 대기열에 등록된 사용자입니다.");
        }
        if (!waitingQueue.hasCapacity(concertId)) {
            queueMetrics.incrementQueueRejected(concertId, "full");
            throw new QueueFullException("현재 대기 인원이 많아 접수가 어렵습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
