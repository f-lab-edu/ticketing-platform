package com.ticket_service.common.redis;

public class LockKey {
    private static final String TICKET_STOCK_LOCK_PREFIX = "LOCK:TICKET_STOCK:";

    public static String ticketStock(Long id) {
        return TICKET_STOCK_LOCK_PREFIX + id;
    }
}
