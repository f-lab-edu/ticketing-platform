package com.ticket_service.queue.service.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueueEnterMessage {

    private Long concertId;
    private String userId;
    private String token;

    public static QueueEnterMessage of(Long concertId, String userId, String token) {
        return new QueueEnterMessage(concertId, userId, token);
    }
}
