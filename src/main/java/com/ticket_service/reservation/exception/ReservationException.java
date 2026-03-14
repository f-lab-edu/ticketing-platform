package com.ticket_service.reservation.exception;

public class ReservationException extends RuntimeException {
    public ReservationException(String message) {
        super(message);
    }

    public static ReservationException paymentFailed() {
        return new ReservationException("결제 처리에 실패했습니다");
    }

    public static ReservationException seatHoldExpired() {
        return new ReservationException("좌석 선점 시간이 만료되었습니다. 좌석을 다시 선택해주세요.");
    }

    public static ReservationException concertNotFound() {
        return new ReservationException("콘서트를 찾을 수 없습니다.");
    }

    public static ReservationException seatStatusMismatch() {
        return new ReservationException("좌석 상태가 일치하지 않습니다. 다시 시도해주세요.");
    }
}
