package com.ticket_service.ticket.exception;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.common.metrics.QueueMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.ticket_service.ticket")
@RequiredArgsConstructor
public class TicketExceptionHandler {

    private final QueueMetrics queueMetrics;

    @ExceptionHandler(InsufficientTicketStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleInsufficientStock(InsufficientTicketStockException e) {
        log.debug("Ticket stock exhausted: {}", e.getMessage());
        queueMetrics.incrementPurchaseFailedInsufficientStock();
        return ApiResponse.of(HttpStatus.CONFLICT, e.getMessage(), null);
    }
}
