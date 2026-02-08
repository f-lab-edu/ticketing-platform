package com.ticket_service.ticket.service;

import com.ticket_service.concert.repository.ConcertRepository;
import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import com.ticket_service.ticket.repository.TicketStockRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private ConcertRepository concertRepository;

    private static final int THREAD_POOL_SIZE = 32;

    private final Set<Long> createdTicketStockIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> createdConcertIds = ConcurrentHashMap.newKeySet();

    private TicketStockTestHelper testHelper;

    @BeforeEach
    void setUp() {
        testHelper = new TicketStockTestHelper(ticketStockRepository, concertRepository, createdTicketStockIds, createdConcertIds);
    }

    @AfterEach
    void tearDown() {
        testHelper.cleanUp();
    }

    @DisplayName("100개 재고에 100개 요청 - 모두 성공 (synchronized)")
    @Test
    void decrease_100_stocks_with_100_requests_all_success() throws Exception {
        // given
        int initialQuantity = 100;
        int threadCount = 100;
        int requestQuantityPerThread = 1;

        TicketStock ticketStock = testHelper.createTicketStock(initialQuantity);
        Long concertId = ticketStock.getConcert().getId();

        //when
        executeConcurrentDecrease(concertId, threadCount, requestQuantityPerThread);

        // then
        TicketStock result = testHelper.findTicketStockByConcertId(concertId);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }

    @DisplayName("100개 재고에 50명이 각각 2개씩 요청 - 모두 성공 (synchronized)")
    @Test
    void decrease_100_stocks_with_50_requests_of_2_quantity() throws Exception {
        // given
        int initialQuantity = 100;
        int threadCount = 50;
        int requestQuantityPerThread = 2;

        TicketStock ticketStock = testHelper.createTicketStock(initialQuantity);
        Long concertId = ticketStock.getConcert().getId();

        // when
        executeConcurrentDecrease(concertId, threadCount, requestQuantityPerThread);

        // then
        TicketStock result = testHelper.findTicketStockByConcertId(concertId);
        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }

    @DisplayName("50개 재고에 100개 요청 - 50개만 성공")
    @Test
    void decrease_50_stocks_with_100_requests_only_50_success() throws Exception {
        // given
        int initialQuantity = 50;
        int threadCount = 100;
        int requestQuantityPerThread = 1;

        TicketStock ticketStock = testHelper.createTicketStock(initialQuantity);
        Long concertId = ticketStock.getConcert().getId();

        // when
        ConcurrentResult result = executeConcurrentDecreaseWithCount(concertId, threadCount, requestQuantityPerThread);

        // then
        TicketStock findStock = testHelper.findTicketStockByConcertId(concertId);
        assertThat(findStock.getRemainingQuantity()).isEqualTo(0);
        assertThat(result.successCount).isEqualTo(50);
        assertThat(result.failCount).isEqualTo(50);
    }

    private void executeConcurrentDecrease(Long concertId, int threadCount, int requestQuantity)
            throws InterruptedException {

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ticketStockService.decreaseByConcertId(concertId, requestQuantity);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
    }

    private ConcurrentResult executeConcurrentDecreaseWithCount(
            Long concertId, int threadCount, int requestQuantity) throws InterruptedException {

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ticketStockService.decreaseByConcertId(concertId, requestQuantity);
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

        return new ConcurrentResult(successCount.get(), failCount.get());
    }

    private record ConcurrentResult(int successCount, int failCount) {}

}
