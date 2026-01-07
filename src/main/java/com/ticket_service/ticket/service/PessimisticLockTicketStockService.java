package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "ticket.lock.strategy", havingValue = "pessimistic")
@RequiredArgsConstructor
public class PessimisticLockTicketStockService implements TicketStockService {
    private final TicketStockRepository ticketStockRepository;

    @Transactional
    public void decrease(Long ticketStockId, int requestQuantity) {
        TicketStock ticketStock = ticketStockRepository.findByIdWithPessimisticLock(ticketStockId)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));

        ticketStock.decreaseQuantity(requestQuantity);
    }
}
