package com.ticket_service.ticket.exception;

public class InsufficientTicketStockException extends RuntimeException {

    private final int remainingQuantity;
    private final int requestQuantity;

    public InsufficientTicketStockException(int remainingQuantity, int requestQuantity) {
        this.remainingQuantity = remainingQuantity;
        this.requestQuantity = requestQuantity;
    }

    public int getRemainingQuantity() {
        return remainingQuantity;
    }

    public int getRequestQuantity() {
        return requestQuantity;
    }
}

