package com.ticket_service.seat.entity;

public enum SeatStatus {
    AVAILABLE,  // 선택 가능
    HELD,       // 임시 선점 (5분 잠금)
    RESERVED    // 예약 확정
}
