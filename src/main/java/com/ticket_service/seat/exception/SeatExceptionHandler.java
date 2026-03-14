package com.ticket_service.seat.exception;

import com.ticket_service.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class SeatExceptionHandler {

    @ExceptionHandler(SeatNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleSeatNotFound(SeatNotFoundException e) {
        log.warn("좌석을 찾을 수 없음: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(SeatAlreadyHeldException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleSeatAlreadyHeld(SeatAlreadyHeldException e) {
        log.warn("좌석 선점 충돌: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(SeatAlreadyReservedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleSeatAlreadyReserved(SeatAlreadyReservedException e) {
        log.warn("좌석 이미 예약됨: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(SeatNotHeldByUserException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleSeatNotHeldByUser(SeatNotHeldByUserException e) {
        log.warn("좌석 소유권 불일치: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    @ExceptionHandler(SeatHoldExpiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleSeatHoldExpired(SeatHoldExpiredException e) {
        log.warn("좌석 선점 만료: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }
}
