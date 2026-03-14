package com.ticket_service.seat.exception;

public class SeatNotHeldByUserException extends RuntimeException {

    public SeatNotHeldByUserException() {
        super("해당 좌석을 선점하지 않았습니다.");
    }
}
