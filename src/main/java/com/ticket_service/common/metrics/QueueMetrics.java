package com.ticket_service.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class QueueMetrics {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> enterCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rejectedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> processingEnteredCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> waitingTimers = new ConcurrentHashMap<>();

    // Counters for ticket purchase
    private final Counter purchaseSuccessCounter;
    private final Counter purchaseFailedInsufficientStockCounter;
    private final Counter purchaseFailedAccessDeniedCounter;
    private final Timer purchaseDurationTimer;

    // Stock metrics
    private final ConcurrentHashMap<String, Counter> stockSoldCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistributionSummary> soldQuantitySummaries = new ConcurrentHashMap<>();

    public QueueMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.purchaseSuccessCounter = Counter.builder("ticket.purchase.success")
                .description("Total successful ticket purchases")
                .register(meterRegistry);

        this.purchaseFailedInsufficientStockCounter = Counter.builder("ticket.purchase.failed")
                .tag("reason", "insufficient_stock")
                .description("Total failed ticket purchases due to insufficient stock")
                .register(meterRegistry);

        this.purchaseFailedAccessDeniedCounter = Counter.builder("ticket.purchase.failed")
                .tag("reason", "access_denied")
                .description("Total failed ticket purchases due to access denied")
                .register(meterRegistry);

        this.purchaseDurationTimer = Timer.builder("ticket.purchase.duration")
                .description("Time taken for ticket purchase")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    // Queue enter metrics
    public void incrementQueueEnter(Long concertId) {
        getOrCreateEnterCounter(concertId).increment();
    }

    public void incrementQueueRejected(Long concertId, String reason) {
        getOrCreateRejectedCounter(concertId, reason).increment();
    }

    public void incrementProcessingEntered(Long concertId) {
        getOrCreateProcessingEnteredCounter(concertId).increment();
    }

    // Waiting time metrics
    public void recordWaitingTime(Long concertId, long waitingTimeMs) {
        getOrCreateWaitingTimer(concertId).record(waitingTimeMs, TimeUnit.MILLISECONDS);
    }

    // Ticket purchase metrics
    public void incrementPurchaseSuccess() {
        purchaseSuccessCounter.increment();
    }

    public void incrementPurchaseFailedInsufficientStock() {
        purchaseFailedInsufficientStockCounter.increment();
    }

    public void incrementPurchaseFailedAccessDenied() {
        purchaseFailedAccessDeniedCounter.increment();
    }

    public Timer getPurchaseDurationTimer() {
        return purchaseDurationTimer;
    }

    // Stock metrics
    public void recordTicketSold(Long concertId, int quantity) {
        getOrCreateStockSoldCounter(concertId).increment(quantity);
        getOrCreateSoldQuantitySummary(concertId).record(quantity);
    }

    private Counter getOrCreateStockSoldCounter(Long concertId) {
        String key = "sold:" + concertId;
        return stockSoldCounters.computeIfAbsent(key, k ->
                Counter.builder("ticket.stock.sold.total")
                        .tag("concertId", String.valueOf(concertId))
                        .description("Total tickets sold (throughput metric)")
                        .register(meterRegistry)
        );
    }

    private DistributionSummary getOrCreateSoldQuantitySummary(Long concertId) {
        String key = "quantity:" + concertId;
        return soldQuantitySummaries.computeIfAbsent(key, k ->
                DistributionSummary.builder("ticket.sold.per.purchase")
                        .tag("concertId", String.valueOf(concertId))
                        .description("Tickets sold per purchase transaction")
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }

    private Counter getOrCreateEnterCounter(Long concertId) {
        String key = "enter:" + concertId;
        return enterCounters.computeIfAbsent(key, k ->
                Counter.builder("queue.enter.total")
                        .tag("concertId", String.valueOf(concertId))
                        .description("Total queue enter requests")
                        .register(meterRegistry)
        );
    }

    private Counter getOrCreateRejectedCounter(Long concertId, String reason) {
        String key = "rejected:" + concertId + ":" + reason;
        return rejectedCounters.computeIfAbsent(key, k ->
                Counter.builder("queue.enter.rejected")
                        .tag("concertId", String.valueOf(concertId))
                        .tag("reason", reason)
                        .description("Total rejected queue entries")
                        .register(meterRegistry)
        );
    }

    private Counter getOrCreateProcessingEnteredCounter(Long concertId) {
        String key = "processing:" + concertId;
        return processingEnteredCounters.computeIfAbsent(key, k ->
                Counter.builder("queue.processing.entered")
                        .tag("concertId", String.valueOf(concertId))
                        .description("Total users entered to processing")
                        .register(meterRegistry)
        );
    }

    private Timer getOrCreateWaitingTimer(Long concertId) {
        String key = "waiting:" + concertId;
        return waitingTimers.computeIfAbsent(key, k ->
                Timer.builder("queue.waiting.time")
                        .tag("concertId", String.valueOf(concertId))
                        .description("Time spent waiting in queue")
                        .publishPercentileHistogram()
                        .register(meterRegistry)
        );
    }
}
