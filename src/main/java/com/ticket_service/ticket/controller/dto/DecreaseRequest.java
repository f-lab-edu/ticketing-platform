package com.ticket_service.ticket.controller.dto;

import lombok.Getter;

@Getter
public class DecreaseRequest {
    private final String userId;
    private final int requestQuantity;

    public DecreaseRequest(String userId, int requestQuantity) {
        this.userId = userId;
        this.requestQuantity = requestQuantity;
    }
}
