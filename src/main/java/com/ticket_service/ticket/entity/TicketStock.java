package com.ticket_service.ticket.entity;

import com.ticket_service.concert.entity.Concert;
import jakarta.persistence.*;

@Entity
public class TicketStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Concert concert;

    private int totalQuantity;

    private int remainingQuantity;
}
