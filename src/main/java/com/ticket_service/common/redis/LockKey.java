package com.ticket_service.common.redis;

public class LockKey {
    // DB TicketStock 테이블 접근 시 사용하는 분산락
    private static final String TICKET_STOCK_LOCK_PREFIX = "LOCK:TICKET_STOCK:";
    // 대기열 사용자 중복 진입 방지용 락
    private static final String QUEUE_USER_LOCK_PREFIX = "LOCK:QUEUE:USER:";
    // 대기열 → 처리열 일괄 입장 시 사용하는 분산락
    private static final String QUEUE_ENTER_LOCK_PREFIX = "LOCK:QUEUE:ENTER:";

    public static String ticketStock(Long id) {
        return TICKET_STOCK_LOCK_PREFIX + id;
    }

    public static String queueUser(Long concertId, String userId) {
        return QUEUE_USER_LOCK_PREFIX + concertId + ":" + userId;
    }

    public static String queueEnter(Long concertId) {
        return QUEUE_ENTER_LOCK_PREFIX + concertId;
    }
}
