package com.ticket_service.ticket.controller.dto;

import lombok.Getter;

@Getter
public class PurchaseRequest {
    private final String userId;
    private final int requestQuantity;

    public PurchaseRequest(String userId, int requestQuantity) {
        this.userId = userId;
        this.requestQuantity = requestQuantity;
    }
}
