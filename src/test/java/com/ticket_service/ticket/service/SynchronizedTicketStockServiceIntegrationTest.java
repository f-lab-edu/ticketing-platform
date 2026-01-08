package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import com.ticket_service.ticket.repository.TicketStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "ticket.lock.strategy=synchronized"
})
class SynchronizedTicketStockServiceIntegrationTest {
    @Autowired
    private TicketStockService ticketStockService;

    @Autowired
    private TicketStockRepository ticketStockRepository;

    private static final int THREAD_POOL_SIZE = 32;

    private final Set<Long> createdTicketStockIds = ConcurrentHashMap.newKeySet();

    @AfterEach
    void tearDown() {
        if (!createdTicketStockIds.isEmpty()) {
            ticketStockRepository.deleteAllByIdInBatch(createdTicketStockIds);
            createdTicketStockIds.clear();
        }
    }

    @DisplayName("100개 재고에 100개 요청 - 모두 성공 (synchronized)")
    @Test
    void decrease_100_stocks_with_100_requests_all_success() throws Exception {
        // given
        int initialQuantity = 100;
        int threadCount = 100;
        int requestQuantityPerThread = 1;

        TicketStock ticketStock = createTicketStock(initialQuantity);

        // when
        executeConcurrentDecrease(ticketStock.getId(), threadCount, requestQuantityPerThread);

        // then
        TicketStock result = findTicketStock(ticketStock.getId());
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }

    @DisplayName("100개 재고에 50명이 각각 2개씩 요청 - 모두 성공 (synchronized)")
    @Test
    void decrease_100_stocks_with_50_requests_of_2_quantity() throws Exception {
        // given
        int initialQuantity = 100;
        int threadCount = 50;
        int requestQuantityPerThread = 2;

        TicketStock ticketStock = createTicketStock(initialQuantity);
        Long ticketStockId = ticketStock.getId();

        // when
        executeConcurrentDecrease(ticketStockId, threadCount, requestQuantityPerThread);

        // then
        TicketStock result = findTicketStock(ticketStockId);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }

    @DisplayName("50개 재고에 100개 요청 - 50개만 성공")
    @Test
    void decrease_50_stocks_with_100_requests_only_50_success() throws Exception {
        // given
        int initialQuantity = 50;
        int threadCount = 100;
        int requestQuantityPerThread = 1;

        TicketStock ticketStock = createTicketStock(initialQuantity);
        Long ticketStockId = ticketStock.getId();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ticketStockService.decrease(ticketStockId, requestQuantityPerThread);
                    successCount.incrementAndGet();
                } catch (InsufficientTicketStockException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        TicketStock result = findTicketStock(ticketStock.getId());
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(50);
        assertThat(failCount.get()).isEqualTo(50);
    }

    private void executeConcurrentDecrease(Long ticketStockId, int threadCount, int requestQuantity)
            throws InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ticketStockService.decrease(ticketStockId, requestQuantity);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
    }

    private TicketStock createTicketStock(int quantity) {
        TicketStock ticketStock = ticketStockRepository.save(
                TicketStock.builder()
                        .totalQuantity(quantity)
                        .remainingQuantity(quantity)
                        .build()
        );

        createdTicketStockIds.add(ticketStock.getId());

        return ticketStock;
    }

    private TicketStock findTicketStock(Long id) {
        return ticketStockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));
    }
}