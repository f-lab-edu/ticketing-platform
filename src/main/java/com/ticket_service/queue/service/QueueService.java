package com.ticket_service.queue.service;

import com.ticket_service.queue.domain.QueueInfo;

public interface QueueService {

    Long enqueue(Long ticketStockId, String userId);

    Long getPosition(Long ticketStockId, String userId);

    boolean canEnter(Long ticketStockId, String userId);

    void enter(Long ticketStockId, String userId);

    void complete(Long ticketStockId, String userId);

    void dequeue(Long ticketStockId, String userId);

    QueueInfo registerAndGetInfo(Long ticketStockId, String userId);

    QueueInfo getQueueInfo(Long ticketStockId, String userId);
}
