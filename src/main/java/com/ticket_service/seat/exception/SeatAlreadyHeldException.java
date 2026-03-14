package com.ticket_service.seat.exception;

public class SeatAlreadyHeldException extends RuntimeException {

    public SeatAlreadyHeldException() {
        super("이미 선택된 좌석입니다.");
    }
}
