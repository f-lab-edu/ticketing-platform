package com.ticket_service.ticket.service;

import com.ticket_service.common.metrics.QueueMetrics;
import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueOrchestrationService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketPurchaseService {

    private final QueueOrchestrationService queueOrchestrationService;
    private final TicketStockService ticketStockService;
    private final QueueMetrics queueMetrics;

    public void purchase(Long concertId, String userId, int quantity) {
        validateQueueAccess(concertId, userId);

        Sample sample = Timer.start();
        try {
            ticketStockService.decreaseByConcertId(concertId, quantity);
            queueMetrics.incrementPurchaseSuccess();
            queueMetrics.recordTicketSold(concertId, quantity);
            log.info("concertId : {}, userId : {}", concertId, userId);
        } finally {
            sample.stop(queueMetrics.getPurchaseDurationTimer());
            queueOrchestrationService.onPurchaseComplete(concertId, userId);
        }
    }

    /**
     * 처리열에 있는 사용자만 구매 가능
     */
    private void validateQueueAccess(Long concertId, String userId) {
        if (!queueOrchestrationService.isInProcessing(concertId, userId)) {
            throw new QueueAccessDeniedException("처리열에 없는 사용자입니다. 대기열을 통해 입장해주세요.");
        }
    }
}
