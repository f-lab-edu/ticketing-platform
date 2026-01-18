package com.ticket_service.queue.controller.dto;

import lombok.Getter;

@Getter
public class QueueEnterRequest {
    private final String userId;

    public QueueEnterRequest(String userId) {
        this.userId = userId;
    }
}
