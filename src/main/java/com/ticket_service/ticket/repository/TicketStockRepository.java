package com.ticket_service.ticket.repository;

import com.ticket_service.ticket.entity.TicketStock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStockRepository extends JpaRepository<TicketStock, Long> {
}
