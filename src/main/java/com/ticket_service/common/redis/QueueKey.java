package com.ticket_service.common.redis;

public class QueueKey {
    private static final String WAITING_QUEUE_PREFIX = "QUEUE:WAITING:";
    private static final String PROCESSING_QUEUE_PREFIX = "QUEUE:PROCESSING:";

    public static String waitingQueue(Long ticketStockId) {
        return WAITING_QUEUE_PREFIX + ticketStockId;
    }

    public static String processingQueue(Long ticketStockId) {
        return PROCESSING_QUEUE_PREFIX + ticketStockId;
    }
}
