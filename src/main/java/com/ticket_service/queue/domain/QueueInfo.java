package com.ticket_service.queue.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueInfo {
    private final String userId;
    private final Long ticketStockId;
    private final Long position;
    private final boolean canEnter;
    private final QueueStatus status;
}
