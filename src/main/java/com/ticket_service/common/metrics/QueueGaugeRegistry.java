package com.ticket_service.common.metrics;

import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.service.ProcessingCounter;
import com.ticket_service.queue.service.SseEmitterService;
import com.ticket_service.queue.service.WaitingQueue;
import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueGaugeRegistry {

    private static final String GAUGE_LOCK_KEY = "metrics:gauge:lock";

    private final MeterRegistry meterRegistry;
    private final WaitingQueue waitingQueue;
    private final ProcessingCounter processingCounter;
    private final SseEmitterService sseEmitterService;
    private final RedissonLockTemplate redissonLockTemplate;
    private final TicketStockRepository ticketStockRepository;

    private final ConcurrentHashMap<Long, AtomicLong> waitingGaugeValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> processingGaugeValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> stockRemainingGaugeValues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> stockTotalGaugeValues = new ConcurrentHashMap<>();
    private final Set<Long> knownConcertIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong sseConnectionsGauge = new AtomicLong(0);

    private volatile boolean sseGaugeRegistered = false;

    @Scheduled(fixedRate = 5000)
    public void updateQueueGauges() {
        updateRedisGaugesWithLock();
        updateSseConnectionsGauge();
    }

    private void updateRedisGaugesWithLock() {
        redissonLockTemplate.tryExecuteWithLock(GAUGE_LOCK_KEY, () -> {
            Set<Long> activeConcertIds = sseEmitterService.getActiveConcertIds();
            knownConcertIds.addAll(activeConcertIds);

            for (Long concertId : knownConcertIds) {
                updateWaitingGauge(concertId);
                updateProcessingGauge(concertId);
                updateStockGauges(concertId);
            }
        });
    }

    private void updateWaitingGauge(Long concertId) {
        AtomicLong gaugeValue = waitingGaugeValues.computeIfAbsent(concertId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("queue.waiting.size", value, AtomicLong::get)
                    .tag("concertId", String.valueOf(id))
                    .description("Current waiting queue size")
                    .register(meterRegistry);
            return value;
        });
        gaugeValue.set(waitingQueue.size(concertId));
    }

    private void updateProcessingGauge(Long concertId) {
        AtomicLong gaugeValue = processingGaugeValues.computeIfAbsent(concertId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("queue.processing.size", value, AtomicLong::get)
                    .tag("concertId", String.valueOf(id))
                    .description("Current processing set size")
                    .register(meterRegistry);
            return value;
        });
        gaugeValue.set(processingCounter.getCount(concertId));
    }

    private void updateSseConnectionsGauge() {
        if (!sseGaugeRegistered) {
            Gauge.builder("sse.connections.active", sseConnectionsGauge, AtomicLong::get)
                    .description("Active SSE connections")
                    .register(meterRegistry);
            sseGaugeRegistered = true;
        }
        sseConnectionsGauge.set(sseEmitterService.getActiveConnectionCount());
    }

    private void updateStockGauges(Long concertId) {
        Optional<TicketStock> stockOpt = ticketStockRepository.findByConcertId(concertId);
        if (stockOpt.isEmpty()) {
            return;
        }

        TicketStock stock = stockOpt.get();

        AtomicLong remainingGauge = stockRemainingGaugeValues.computeIfAbsent(concertId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("ticket.stock.remaining", value, AtomicLong::get)
                    .tag("concertId", String.valueOf(id))
                    .description("Remaining ticket stock")
                    .register(meterRegistry);
            return value;
        });
        remainingGauge.set(stock.getRemainingQuantity());

        AtomicLong totalGauge = stockTotalGaugeValues.computeIfAbsent(concertId, id -> {
            AtomicLong value = new AtomicLong(0);
            Gauge.builder("ticket.stock.total", value, AtomicLong::get)
                    .tag("concertId", String.valueOf(id))
                    .description("Total ticket stock")
                    .register(meterRegistry);
            return value;
        });
        totalGauge.set(stock.getTotalQuantity());
    }
}
