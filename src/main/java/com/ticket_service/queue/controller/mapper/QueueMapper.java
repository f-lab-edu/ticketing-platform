package com.ticket_service.queue.controller.mapper;

import com.ticket_service.queue.controller.dto.QueueStatusResponse;
import com.ticket_service.queue.domain.QueueInfo;
import org.springframework.stereotype.Component;

@Component
public class QueueMapper {

    public QueueStatusResponse toResponse(QueueInfo queueInfo) {
        return QueueStatusResponse.builder()
                .userId(queueInfo.getUserId())
                .ticketStockId(queueInfo.getTicketStockId())
                .position(queueInfo.getPosition())
                .canEnter(queueInfo.isCanEnter())
                .status(queueInfo.getStatus())
                .build();
    }
}
