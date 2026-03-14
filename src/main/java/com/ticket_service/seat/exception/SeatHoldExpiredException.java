package com.ticket_service.seat.exception;

public class SeatHoldExpiredException extends RuntimeException {
    public SeatHoldExpiredException() {
        super("좌석 선점 시간이 만료되었습니다.");
    }
}
