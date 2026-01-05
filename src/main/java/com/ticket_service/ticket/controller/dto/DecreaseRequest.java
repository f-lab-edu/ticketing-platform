package com.ticket_service.ticket.controller.dto;

import lombok.Getter;

@Getter
public class DecreaseRequest {
    private final int requestQuantity;

    public DecreaseRequest(int requestQuantity) {
        this.requestQuantity = requestQuantity;
    }
}
