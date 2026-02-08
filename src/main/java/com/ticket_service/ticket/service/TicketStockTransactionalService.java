package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketStockTransactionalService {
    private final TicketStockRepository ticketStockRepository;

    @Transactional
    public void decreaseByConcertId(Long concertId, int requestQuantity) {
        TicketStock ticketStock = ticketStockRepository.findByConcertId(concertId)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));

        ticketStock.decreaseQuantity(requestQuantity);
    }
}
