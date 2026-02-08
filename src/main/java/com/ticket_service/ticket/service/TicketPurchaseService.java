package com.ticket_service.ticket.service;

import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketPurchaseService {

    private final QueueOrchestrationService queueOrchestrationService;
    private final TicketStockService ticketStockService;

    public void purchase(Long concertId, String userId, int quantity) {
        validateQueueAccess(concertId, userId);

        try {
            ticketStockService.decreaseByConcertId(concertId, quantity);
        } finally {
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
