package com.ticket_service.ticket.exception;

import com.ticket_service.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.ticket_service.ticket")
public class TicketExceptionHandler {
    @ExceptionHandler(InsufficientTicketStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInsufficientStock(InsufficientTicketStockException e) {
        log.debug("Ticket stock exhausted: {}", e.getMessage());

        return ApiResponse.of(HttpStatus.CONFLICT, e.getMessage(), null);
    }
}
