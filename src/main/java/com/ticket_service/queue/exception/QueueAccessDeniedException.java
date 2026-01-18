package com.ticket_service.queue.exception;

public class QueueAccessDeniedException extends RuntimeException {
    public QueueAccessDeniedException(String message) {
        super(message);
    }
}
