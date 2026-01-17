package com.ticket_service.common.redis;

public class LockKey {
    private static final String TICKET_STOCK_LOCK_PREFIX = "LOCK:TICKET_STOCK:";
    private static final String QUEUE_USER_LOCK_PREFIX = "LOCK:QUEUE:USER:";

    public static String ticketStock(Long id) {
        return TICKET_STOCK_LOCK_PREFIX + id;
    }

    public static String queueUser(Long ticketStockId, String userId) {
        return QUEUE_USER_LOCK_PREFIX + ticketStockId + ":" + userId;
    }
}
