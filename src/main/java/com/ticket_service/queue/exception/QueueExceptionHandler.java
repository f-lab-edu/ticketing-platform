package com.ticket_service.queue.exception;

import com.ticket_service.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "com.ticket_service.queue")
public class QueueExceptionHandler {

    @ExceptionHandler(AlreadyInQueueException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleAlreadyInQueue(AlreadyInQueueException e) {
        log.debug("Already in queue: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    @ExceptionHandler(QueueAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleQueueAccessDenied(QueueAccessDeniedException e) {
        log.debug("Queue access denied: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.FORBIDDEN, e.getMessage(), null);
    }

    @ExceptionHandler(NotInQueueException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotInQueue(NotInQueueException e) {
        log.debug("Not in queue: {}", e.getMessage());
        return ApiResponse.of(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }
}
