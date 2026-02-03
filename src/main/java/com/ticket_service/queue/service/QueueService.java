package com.ticket_service.queue.service;

import java.util.List;

public interface QueueService {

    Long enterWaitingQueue(Long concertId, String userId);

    void completeProcessing(Long concertId, String userId);

    void removeFromQueue(Long concertId, String userId);

    boolean isInProcessing(Long concertId, String userId);

    List<String> permitProcessing(Long concertId);

    boolean hasProcessingCapacity(Long concertId);

    List<String> getWaitingUsers(Long concertId);
}
