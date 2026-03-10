package com.ticket_service.ticket.service;

import com.ticket_service.common.metrics.QueueMetrics;
import com.ticket_service.queue.service.QueueOrchestrationService;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @deprecated 새로운 좌석 선택 기반 예약 시스템으로 대체되었습니다.
 * 새로운 흐름: 좌석 조회 → 좌석 선택 → 예약 확정 (ReservationService 사용)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Deprecated
public class TicketPurchaseService {

    private final QueueOrchestrationService queueOrchestrationService;
    private final TicketStockService ticketStockService;
    private final QueueMetrics queueMetrics;

    /**
     * @deprecated ReservationService.createReservation()을 사용하세요.
     */
    @Deprecated
    public void purchase(Long concertId, String userId, int quantity) {
        Sample sample = Timer.start();
        try {
            ticketStockService.decreaseByConcertId(concertId, quantity);
            queueMetrics.incrementPurchaseSuccess();
            queueMetrics.recordTicketSold(concertId, quantity);
            log.info("concertId : {}, userId : {}", concertId, userId);
        } finally {
            sample.stop(queueMetrics.getPurchaseDurationTimer());
            queueOrchestrationService.onReservationComplete(concertId, userId);
        }
    }
}
