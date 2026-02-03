package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEnterEvent;
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

    private final QueueService queueService;
    private final SseEmitterService sseEmitterService;

    /**
     * 대기열 등록 + SSE 구독
     * 등록 후 대기 순번 또는 즉시 입장 이벤트를 전송한다.
     */
    public SseEmitter registerAndSubscribe(Long concertId, String userId) {
        Long position = queueService.enterWaitingQueue(concertId, userId);
        SseEmitter emitter = sseEmitterService.createEmitter(concertId, userId);

        if (queueService.hasProcessingCapacity(concertId)) {
            enterNextAndNotify(concertId);
        } else {
            sseEmitterService.sendEvent(concertId, userId, QueueEventType.QUEUE_POSITION, new QueuePositionEvent(position));
        }

        return emitter;
    }

    /**
     * 구매 완료 시 호출
     * 처리열에서 제거 → 다음 대기자 입장
     */
    public void onPurchaseComplete(Long concertId, String userId) {
        queueService.completeProcessing(concertId, userId);
        enterNextAndNotify(concertId);
    }

    public boolean isInProcessing(Long concertId, String userId) {
        return queueService.isInProcessing(concertId, userId);
    }

    /**
     * 취소 시 호출
     * 대기열/처리열에서 제거 → SSE 종료 → 다음 대기자 입장
     */
    public void onCancel(Long concertId, String userId) {
        queueService.removeFromQueue(concertId, userId);
        sseEmitterService.completeEmitter(concertId, userId);
        enterNextAndNotify(concertId);
    }

    /**
     * 다음 대기자들을 입장시키고 입장 완료 SSE 이벤트를 전송한다.
     * 이후 남은 대기자들에게 갱신된 순번을 전송한다.
     */
    private void enterNextAndNotify(Long concertId) {
        List<String> enteredUsers = queueService.permitProcessing(concertId);
        for (String enteredUserId : enteredUsers) {
            sseEmitterService.sendEvent(concertId, enteredUserId, QueueEventType.ENTER, QueueEnterEvent.processing());
            sseEmitterService.completeEmitter(concertId, enteredUserId);
        }

        List<String> waitingUsers = queueService.getWaitingUsers(concertId);
        for (int i = 0; i < waitingUsers.size(); i++) {
            sseEmitterService.sendEvent(concertId, waitingUsers.get(i), QueueEventType.QUEUE_POSITION, new QueuePositionEvent(i));
        }
    }
}
