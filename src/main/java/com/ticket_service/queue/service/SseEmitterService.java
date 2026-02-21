package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEventType;
import com.ticket_service.queue.service.dto.QueuePositionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SseEmitterService {

    @Value("${queue.sse-timeout}")
    private Duration sseTimeout;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long concertId, String userId) {
        String key = buildKey(concertId, userId);

        emitters.computeIfPresent(key, (k, existing) -> {
            existing.complete();
            return null;
        });

        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
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

    /**
     * 이벤트 전송 후 emitter를 완료 처리한다. (원자적 연산)
     * Pub/Sub에서 enter 이벤트 수신 시 사용한다.
     *
     * @return emitter가 존재하고 전송 성공 시 true
     */
    public boolean sendEventAndComplete(Long concertId, String userId, QueueEventType eventType, Object data) {
        String key = buildKey(concertId, userId);
        AtomicBoolean sent = new AtomicBoolean(false);

        emitters.computeIfPresent(key, (k, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType.getValue())
                        .data(data));
                sent.set(true);
                emitter.complete();
            } catch (IOException e) {
                log.warn("SSE 이벤트 전송 실패: concertId={}, userId={}", concertId, userId, e);
            }
            return null;
        });

        return sent.get();
    }

    public void completeEmitter(Long concertId, String userId) {
        String key = buildKey(concertId, userId);
        emitters.computeIfPresent(key, (k, emitter) -> {
            emitter.complete();
            return null;
        });
    }

    private String buildKey(Long concertId, String userId) {
        return concertId + ":" + userId;
    }

    public Set<Long> getActiveConcertIds() {
        return emitters.keySet().stream()
                .map(key -> Long.parseLong(key.split(":")[0]))
                .collect(Collectors.toSet());
    }

    public void broadcastPositions(Long concertId, List<String> waitingUsers) {
        for (int i = 0; i < waitingUsers.size(); i++) {
            sendEvent(concertId, waitingUsers.get(i), QueueEventType.QUEUE_POSITION, new QueuePositionEvent(i));
        }
    }

    private void registerCallbacks(SseEmitter emitter, String key, Long concertId, String userId) {
        emitter.onCompletion(() -> emitters.remove(key, emitter));

        emitter.onTimeout(() -> {
            emitter.complete();
            log.info("SSE 연결 타임아웃: key={}", key);
        });

        emitter.onError(e -> {
            log.warn("SSE 연결 오류: concertId={}, userId={}", concertId, userId, e);
        });
    }
}
