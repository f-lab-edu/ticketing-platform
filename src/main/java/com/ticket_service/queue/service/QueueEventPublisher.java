package com.ticket_service.queue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_service.queue.service.dto.QueueEnterMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEventPublisher {

    public static final String QUEUE_ENTER_CHANNEL = "queue:enter";

    private final RedisTemplate<String, String> queueRedisTemplate;
    private final ObjectMapper objectMapper;

    @Async("sseTaskExecutor")
    public void publishEnterEvent(Long concertId, String userId, String token) {
        try {
            QueueEnterMessage message = QueueEnterMessage.of(concertId, userId, token);
            String json = objectMapper.writeValueAsString(message);
            queueRedisTemplate.convertAndSend(QUEUE_ENTER_CHANNEL, json);
            log.info("[PUB] 입장 이벤트 발행: concertId={}, userId={}", concertId, userId);
        } catch (JsonProcessingException e) {
            log.error("입장 이벤트 직렬화 실패: concertId={}, userId={}", concertId, userId, e);
        }
    }
}
