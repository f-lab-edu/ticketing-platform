package com.ticket_service.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class QueueTokenClaims {
    private final Long concertId;
    private final String userId;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
