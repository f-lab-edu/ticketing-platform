package com.ticket_service.ticket.service;

public interface TicketStockService {
    void decreaseByConcertId(Long concertId, int requestQuantity);
}
