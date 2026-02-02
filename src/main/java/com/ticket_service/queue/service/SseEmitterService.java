package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SseEmitterService {

    @Value("${queue.sse-timeout-milliseconds:600000}")
    private long sseTimeoutMilliseconds;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long concertId, String userId) {
        String key = buildKey(concertId, userId);

        emitters.computeIfPresent(key, (k, existing) -> {
            existing.complete();
            return null;
        });

        SseEmitter emitter = new SseEmitter(sseTimeoutMilliseconds);
        registerCallbacks(emitter, key, concertId, userId);

        emitters.put(key, emitter);
        return emitter;
    }

    public void sendEvent(Long concertId, String userId, QueueEventType eventType, Object data) {
        String key = buildKey(concertId, userId);

        emitters.computeIfPresent(key, (k, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType.getValue())
                        .data(data));
                return emitter;
            } catch (IOException e) {
                log.warn("SSE 이벤트 전송 실패: concertId={}, userId={}", concertId, userId, e);
                return null;
            }
        });
    }

    public void completeEmitter(Long concertId, String userId) {
        String key = buildKey(concertId, userId);
        SseEmitter emitter = emitters.get(key);
        if (emitter != null) {
            emitter.complete();
        }
    }

    private String buildKey(Long concertId, String userId) {
        return concertId + ":" + userId;
    }

    private void registerCallbacks(SseEmitter emitter, String key, Long concertId, String userId) {
        emitter.onCompletion(() -> {
            emitters.remove(key);
            log.debug("SSE 연결 정상 종료: key={}", key);
        });

        emitter.onTimeout(() -> {
            emitter.complete();
            log.info("SSE 연결 타임아웃: key={}", key);
        });

        emitter.onError(e -> {
            log.warn("SSE 연결 오류: concertId={}, userId={}", concertId, userId, e);
        });
    }
}
