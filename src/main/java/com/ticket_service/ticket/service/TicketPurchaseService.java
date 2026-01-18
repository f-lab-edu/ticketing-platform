package com.ticket_service.ticket.service;

import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TicketPurchaseService {

    private final QueueService queueService;
    private final TicketStockService ticketStockService;

    public void purchase(Long ticketStockId, String userId, int quantity) {
        validateQueueAccess(ticketStockId, userId);
        queueService.enter(ticketStockId, userId);

        try {
            ticketStockService.decrease(ticketStockId, quantity);
        } finally {
            queueService.complete(ticketStockId, userId);
        }
    }

    /**
     * 대기열을 무시하고 직접 API를 호출하는 것을 방지
     */
    private void validateQueueAccess(Long ticketStockId, String userId) {
        if (!queueService.canEnter(ticketStockId, userId)) {
            throw new QueueAccessDeniedException("대기열 순서가 아닙니다. 대기열 상태를 확인해주세요.");
        }
    }
}
