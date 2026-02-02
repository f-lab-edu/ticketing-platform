package com.ticket_service.queue.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueEnterEvent {

    private final String status;

    public static QueueEnterEvent processing() {
        return new QueueEnterEvent("PROCESSING");
    }
}
