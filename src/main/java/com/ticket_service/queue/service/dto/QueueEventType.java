package com.ticket_service.queue.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QueueEventType {

    QUEUE_POSITION("queue-position"),
    ENTER("enter");

    private final String value;
}
