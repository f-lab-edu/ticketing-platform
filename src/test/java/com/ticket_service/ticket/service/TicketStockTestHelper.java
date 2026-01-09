package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;

import java.util.Set;

public class TicketStockTestHelper {

    private final TicketStockRepository ticketStockRepository;
    private final Set<Long> createdTicketStockIds;

    private static final int THREAD_POOL_SIZE = 32;

    public TicketStockTestHelper(
            TicketStockRepository ticketStockRepository,
            Set<Long> createdTicketStockIds) {
        this.ticketStockRepository = ticketStockRepository;
        this.createdTicketStockIds = createdTicketStockIds;
    }

    public TicketStock createTicketStock(int quantity) {
        TicketStock ticketStock = ticketStockRepository.save(
                TicketStock.builder()
                        .totalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .build()
        );

        createdTicketStockIds.add(ticketStock.getId());
        return ticketStock;
    }

    public TicketStock findTicketStock(Long id) {
        return ticketStockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));
    }
}
