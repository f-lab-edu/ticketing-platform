package com.ticket_service.queue.service;

public record PolledUser(String userId, long waitingTimeMs) {
}
