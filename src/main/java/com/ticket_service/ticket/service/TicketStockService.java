package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketStockService {
    private final TicketStockRepository ticketStockRepository;

    @Transactional
    public synchronized void decrease(Long ticketStockId, int requestQuantity) {
        TicketStock ticketStock = ticketStockRepository.findById(ticketStockId)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));

        ticketStock.decreaseQuantity(requestQuantity);
    }
}
