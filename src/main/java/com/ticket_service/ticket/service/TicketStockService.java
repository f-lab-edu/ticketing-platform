package com.ticket_service.ticket.service;

public interface TicketStockService {
    void decrease(Long id, int quantity);
}
