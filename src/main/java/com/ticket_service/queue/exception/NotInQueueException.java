package com.ticket_service.queue.exception;

public class NotInQueueException extends RuntimeException {
    public NotInQueueException(String message) {
        super(message);
    }
}
