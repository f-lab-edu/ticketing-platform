package com.ticket_service.queue.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueuePositionBatchScheduler {

    private final QueueService queueService;
    private final SseEmitterService sseEmitterService;

    @Scheduled(fixedDelayString = "${queue.position-broadcast-interval:5s}")
    public void broadcastPositions() {
        Set<Long> activeConcertIds = sseEmitterService.getActiveConcertIds();

        for (Long concertId : activeConcertIds) {
            try {
                List<String> waitingUsers = queueService.getWaitingUsers(concertId);
                if (!waitingUsers.isEmpty()) {
                    sseEmitterService.broadcastPositions(concertId, waitingUsers);
                    log.debug("순번 브로드캐스트 완료: concertId={}, userCount={}", concertId, waitingUsers.size());
                }
            } catch (Exception e) {
                log.error("순번 브로드캐스트 실패: concertId={}", concertId, e);
            }
        }
    }
}
