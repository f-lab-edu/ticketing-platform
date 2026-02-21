package com.ticket_service.queue.service;

import com.ticket_service.common.redis.LockKey;
import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import com.ticket_service.queue.exception.QueueFullException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisQueueService implements QueueService {

    private final WaitingQueue waitingQueue;
    private final ProcessingSet processingSet;
    private final RedissonLockTemplate redissonLockTemplate;

    @Override
    public Long enterWaitingQueue(Long concertId, String userId) {
        return redissonLockTemplate.executeWithLock(
                LockKey.queueUser(concertId, userId),
                () -> {
                    validateEntry(concertId, userId);
                    waitingQueue.add(concertId, userId);
                    return getPosition(concertId, userId);
                }
        );
    }

    @Override
    public void completeProcessing(Long concertId, String userId) {
        processingSet.remove(concertId, userId);
    }

    @Override
    public void removeFromQueue(Long concertId, String userId) {
        waitingQueue.remove(concertId, userId);
        processingSet.remove(concertId, userId);
    }

    @Override
    public boolean isInProcessing(Long concertId, String userId) {
        return processingSet.contains(concertId, userId);
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
        return processingSet.hasCapacity(concertId);
    }

    @Override
    public List<String> getWaitingUsers(Long concertId) {
        return waitingQueue.getAll(concertId);
    }

    public Long getPosition(Long concertId, String userId) {
        return waitingQueue.rank(concertId, userId);
    }

    private List<String> moveToProcessing(Long concertId) {
        long limit = processingSet.remainingCapacity(concertId);
        if (limit <= 0) {
            return List.of();
        }

        List<String> enteredUsers = waitingQueue.pollTopUsers(concertId, limit);
        if (!enteredUsers.isEmpty()) {
            processingSet.addAll(concertId, enteredUsers);
        }
        return enteredUsers;
    }

    private String moveOneToProcessing(Long concertId) {
        if (!processingSet.hasCapacity(concertId)) {
            return null;
        }

        String userId = waitingQueue.pollTopUser(concertId);
        if (userId != null) {
            processingSet.add(concertId, userId);
        }
        return userId;
    }

    private void validateEntry(Long concertId, String userId) {
        if (processingSet.contains(concertId, userId)) {
            throw new AlreadyInQueueException("이미 입장한 사용자입니다.");
        }
        if (waitingQueue.contains(concertId, userId)) {
            throw new AlreadyInQueueException("이미 대기열에 등록된 사용자입니다.");
        }
        if (!waitingQueue.hasCapacity(concertId)) {
            throw new QueueFullException("현재 대기 인원이 많아 접수가 어렵습니다. 잠시 후 다시 시도해주세요.");
        }
    }
}
