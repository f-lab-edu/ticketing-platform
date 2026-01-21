package com.ticket_service.queue.domain;

public enum QueueStatus {
    WAITING,
    CAN_ENTER,
    PROCESSING,
    NOT_IN_QUEUE;

    /** 대기열 순번과 입장 가능 여부로 상태 결정 */
    public static QueueStatus determine(Long position, boolean canEnter) {
        if (position == null && canEnter) {
            return PROCESSING;
        } else if (position == null) {
            return NOT_IN_QUEUE;
        } else if (canEnter) {
            return CAN_ENTER;
        } else {
            return WAITING;
        }
    }
}
