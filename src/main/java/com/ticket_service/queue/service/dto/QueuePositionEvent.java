package com.ticket_service.queue.service.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueuePositionEvent {

    private final long position;
}
