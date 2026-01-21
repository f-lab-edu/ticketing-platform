package com.ticket_service.queue.exception;

public class AlreadyInQueueException extends RuntimeException {
    public AlreadyInQueueException(String message) {
        super(message);
    }
}
