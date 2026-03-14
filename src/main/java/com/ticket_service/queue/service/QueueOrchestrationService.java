package com.ticket_service.queue.service;

import com.ticket_service.auth.service.QueueTokenService;
import com.ticket_service.common.redis.LockKey;
import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.service.dto.QueueEventType;
import com.ticket_service.queue.service.dto.QueuePositionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueueOrchestrationService {

    private final WaitingQueue waitingQueue;
    private final ProcessingCounter processingCounter;
    private final QueueTokenService queueTokenService;
    private final SseEmitterService sseEmitterService;
    private final QueueEventPublisher queueEventPublisher;
    private final RedissonLockTemplate redissonLockTemplate;

    /**
     * 대기열 등록 + SSE 구독
     * 등록 후 대기 순번 또는 즉시 입장 이벤트를 전송한다.
     *
     * 중요: emitter를 먼저 생성한 후 대기열에 추가해야 함.
     * 그렇지 않으면 다른 스레드에서 enterNextAndNotify 호출 시
     * emitter가 없어 enter 이벤트를 놓칠 수 있음.
     */
    public SseEmitter registerAndSubscribe(Long concertId, String userId) {
        SseEmitter emitter = sseEmitterService.createEmitter(concertId, userId);

        // 대기열에 추가
        waitingQueue.add(concertId, userId);
        Long position = waitingQueue.rank(concertId, userId);

        if (processingCounter.remainingCapacity(concertId) > 0) {
            enterNextAndNotify(concertId);
        } else if (position != null) {
            sseEmitterService.sendEventAsync(concertId, userId, QueueEventType.QUEUE_POSITION, new QueuePositionEvent(position));
        }

        return emitter;
    }

    /**
     * 예약 완료 시 호출
     * 카운터 감소 → 다음 대기자 입장
     */
    public void onReservationComplete(Long concertId, String userId) {
        processingCounter.decrement(concertId);
        log.info("예약 완료 처리: concertId={}, userId={}", concertId, userId);
        enterNextAndNotify(concertId);
    }

    /**
     * 대기열 취소 시 호출 (아직 입장 전인 사용자)
     * 대기열에서 제거 → SSE 종료
     *
     * 주의: 카운터는 입장 시점에 증가하므로, 대기열 취소 시에는 감소하지 않음
     */
    public void onWaitingCancel(Long concertId, String userId) {
        waitingQueue.remove(concertId, userId);
        sseEmitterService.completeEmitter(concertId, userId);
        log.info("대기열 취소: concertId={}, userId={}", concertId, userId);
    }

    /**
     * 좌석 잠금 만료 시 호출
     * 카운터 감소 → 다음 대기자 입장
     */
    public void onSeatHoldExpired(Long concertId) {
        processingCounter.decrement(concertId);
        log.info("좌석 잠금 만료 처리: concertId={}", concertId);
        enterNextAndNotify(concertId);
    }

    /**
     * 다음 대기자들을 입장시키고 Redis Pub/Sub으로 입장 이벤트를 발행한다.
     * 각 서버의 QueueEventSubscriber가 메시지를 받아 해당 사용자에게 SSE를 전송한다.
     * 순번 업데이트는 QueuePositionBatchScheduler에서 주기적으로 처리한다.
     *
     * 분산락 내에서 실행되므로 카운터를 일괄 증가시킨다.
     */
    private void enterNextAndNotify(Long concertId) {
        redissonLockTemplate.executeWithLock(LockKey.queueEnter(concertId), () -> {
            int capacity = processingCounter.remainingCapacity(concertId);
            if (capacity <= 0) {
                return null;
            }

            List<PolledUser> users = waitingQueue.pollTopUsersWithWaitingTime(concertId, capacity);
            if (users.isEmpty()) {
                return null;
            }

            // 분산락이 보장된 상태이므로 일괄 증가
            processingCounter.incrementBy(concertId, users.size());

            for (PolledUser user : users) {
                String token = queueTokenService.generateToken(concertId, user.userId());
                queueEventPublisher.publishEnterEvent(concertId, user.userId(), token);
                log.info("사용자 입장 처리: concertId={}, userId={}", concertId, user.userId());
            }
            return null;
        });
    }
}
