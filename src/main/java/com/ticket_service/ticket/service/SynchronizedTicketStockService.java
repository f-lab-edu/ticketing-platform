package com.ticket_service.ticket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ticket.lock.strategy", havingValue = "synchronized")
@RequiredArgsConstructor
public class SynchronizedTicketStockService implements TicketStockService {

    private final TicketStockTransactionalService ticketStockTransactionalService;

    @Override
    public synchronized void decreaseByConcertId(Long concertId, int requestQuantity) {
        ticketStockTransactionalService.decreaseByConcertId(concertId, requestQuantity);
    }
}
