package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEventType;
import com.ticket_service.queue.service.dto.QueuePositionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SseEmitterService {

    @Value("${queue.sse-timeout}")
    private Duration sseTimeout;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Executor sseTaskExecutor;

    public SseEmitterService(@Qualifier("sseTaskExecutor") Executor sseTaskExecutor) {
        this.sseTaskExecutor = sseTaskExecutor;
    }

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
     * 비동기로 SSE 이벤트를 전송한다.
     */
    public CompletableFuture<Void> sendEventAsync(Long concertId, String userId, QueueEventType eventType, Object data) {
        return CompletableFuture.runAsync(
                () -> sendEvent(concertId, userId, eventType, data),
                sseTaskExecutor
        );
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

    /**
     * 비동기로 이벤트 전송 후 emitter를 완료 처리한다.
     */
    public CompletableFuture<Boolean> sendEventAndCompleteAsync(Long concertId, String userId, QueueEventType eventType, Object data) {
        return CompletableFuture.supplyAsync(
                () -> sendEventAndComplete(concertId, userId, eventType, data),
                sseTaskExecutor
        );
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

    public int getActiveConnectionCount() {
        return emitters.size();
    }

    public void broadcastPositions(Long concertId, List<String> waitingUsers) {
        for (int i = 0; i < waitingUsers.size(); i++) {
            sendEvent(concertId, waitingUsers.get(i), QueueEventType.QUEUE_POSITION, new QueuePositionEvent(i));
        }
    }

    /**
     * 비동기로 모든 대기자에게 순번 정보를 병렬 전송한다.
     * 모든 전송이 완료될 때까지 기다린다.
     */
    public CompletableFuture<Void> broadcastPositionsAsync(Long concertId, List<String> waitingUsers) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < waitingUsers.size(); i++) {
            final int position = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> sendEvent(concertId, waitingUsers.get(position), QueueEventType.QUEUE_POSITION, new QueuePositionEvent(position)),
                    sseTaskExecutor
            );
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
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
