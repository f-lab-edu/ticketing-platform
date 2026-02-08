package com.ticket_service.ticket.service;

import com.ticket_service.concert.entity.Concert;
import com.ticket_service.concert.repository.ConcertRepository;
import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;

import java.util.Set;

public class TicketStockTestHelper {

    private final TicketStockRepository ticketStockRepository;
    private final ConcertRepository concertRepository;
    private final Set<Long> createdTicketStockIds;
    private final Set<Long> createdConcertIds;

    public TicketStockTestHelper(
            TicketStockRepository ticketStockRepository,
            ConcertRepository concertRepository,
            Set<Long> createdTicketStockIds,
            Set<Long> createdConcertIds) {
        this.ticketStockRepository = ticketStockRepository;
        this.concertRepository = concertRepository;
        this.createdTicketStockIds = createdTicketStockIds;
        this.createdConcertIds = createdConcertIds;
    }

    public TicketStock createTicketStock(int quantity) {
        Concert concert = concertRepository.save(Concert.builder().title("test-concert").build());
        createdConcertIds.add(concert.getId());

        TicketStock ticketStock = ticketStockRepository.save(
                TicketStock.builder()
                        .concert(concert)
                        .totalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .build()
        );

        createdTicketStockIds.add(ticketStock.getId());
        return ticketStock;
    }

    public TicketStock createTicketStock(Concert concert, int quantity) {
        TicketStock ticketStock = ticketStockRepository.save(
                TicketStock.builder()
                        .concert(concert)
                        .totalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .build()
        );

        createdTicketStockIds.add(ticketStock.getId());
        return ticketStock;
    }

    public Concert createConcert() {
        Concert concert = concertRepository.save(Concert.builder().title("test-concert").build());
        createdConcertIds.add(concert.getId());
        return concert;
    }

    public TicketStock findTicketStock(Long id) {
        return ticketStockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));
    }

    public TicketStock findTicketStockByConcertId(Long concertId) {
        return ticketStockRepository.findByConcertId(concertId)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));
    }

    public void cleanUp() {
        if (!createdTicketStockIds.isEmpty()) {
            ticketStockRepository.deleteAllByIdInBatch(createdTicketStockIds);
            createdTicketStockIds.clear();
        }
        if (!createdConcertIds.isEmpty()) {
            concertRepository.deleteAllByIdInBatch(createdConcertIds);
            createdConcertIds.clear();
        }
    }
}
