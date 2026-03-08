package com.ticket_service.queue.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueEnterEvent {

    private final String status;
    private final String token;

    public static QueueEnterEvent processing(String token) {
        return new QueueEnterEvent("PROCESSING", token);
    }
}
