package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest
class TicketStockServiceTest {

    @Autowired
    private TicketStockService ticketStockService;

    @Autowired
    private TicketStockRepository ticketStockRepository;

    @DisplayName("synchronized 메서드는 다중 스레드 환경에서 티켓 재고를 안전하게 차감한다")
    @Test
    void synchronized_decrease_should_be_thread_safe() throws Exception {
        // given
        int initialQuantity = 1000;
        int threadCount = 1000;
        int requestQuantityPerThread = 1;

        TicketStock ticketStock = ticketStockRepository.save(
                TicketStock.builder()
                        .totalQuantity(initialQuantity)
                        .remainingQuantity(initialQuantity)
                        .build()
        );

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    ticketStockService.decrease(ticketStock.getId(), requestQuantityPerThread);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 모든 스레드 종료 대기
        executorService.shutdown();

        // then
        TicketStock result = ticketStockRepository.findById(ticketStock.getId())
                .orElseThrow();

        assertThat(result.getRemainingQuantity()).isEqualTo(0);
    }
}