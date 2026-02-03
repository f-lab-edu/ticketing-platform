package com.ticket_service.common.redis;

public class QueueKey {
    private static final String WAITING_QUEUE_PREFIX = "QUEUE:WAITING:";
    private static final String PROCESSING_SET_PREFIX = "SET:PROCESSING:";

    public static String waitingQueue(Long concertId) {
        return WAITING_QUEUE_PREFIX + concertId;
    }

    public static String processingSet(Long concertId) {
        return PROCESSING_SET_PREFIX + concertId;
    }
}
