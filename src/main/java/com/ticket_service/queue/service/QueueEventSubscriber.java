package com.ticket_service.queue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket_service.queue.service.dto.QueueEnterEvent;
import com.ticket_service.queue.service.dto.QueueEnterMessage;
import com.ticket_service.queue.service.dto.QueueEventType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEventSubscriber implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final SseEmitterService sseEmitterService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void subscribe() {
        redisMessageListenerContainer.addMessageListener(
                this,
                new ChannelTopic(QueueEventPublisher.QUEUE_ENTER_CHANNEL)
        );
        log.info("Redis Pub/Sub 구독 시작: channel={}", QueueEventPublisher.QUEUE_ENTER_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            QueueEnterMessage enterMessage = objectMapper.readValue(json, QueueEnterMessage.class);

            Long concertId = enterMessage.getConcertId();
            String userId = enterMessage.getUserId();

            boolean sent = sseEmitterService.sendEventAndComplete(
                    concertId,
                    userId,
                    QueueEventType.ENTER,
                    QueueEnterEvent.processing()
            );

            if (sent) {
                log.info("[SUB] 입장 이벤트 전송 성공: concertId={}, userId={}", concertId, userId);
            }
        } catch (Exception e) {
            log.error("입장 이벤트 처리 실패", e);
        }
    }
}
