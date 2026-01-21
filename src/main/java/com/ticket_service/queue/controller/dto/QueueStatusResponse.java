package com.ticket_service.queue.controller.dto;

import com.ticket_service.queue.domain.QueueStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QueueStatusResponse {
    private final String userId;
    private final Long ticketStockId;
    private final Long position;
    private final boolean canEnter;
    private final QueueStatus status;
}
