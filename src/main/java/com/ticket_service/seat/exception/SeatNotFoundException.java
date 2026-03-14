package com.ticket_service.seat.exception;

public class SeatNotFoundException extends RuntimeException {

    public SeatNotFoundException() {
        super("좌석을 찾을 수 없습니다.");
    }
}
