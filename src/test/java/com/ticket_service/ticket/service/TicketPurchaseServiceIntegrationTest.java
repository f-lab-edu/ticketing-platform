package com.ticket_service.ticket.service;

import com.ticket_service.common.redis.QueueKey;
import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueService;
import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import com.ticket_service.ticket.repository.TicketStockRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class TicketPurchaseServiceIntegrationTest {

    @Autowired
    private TicketPurchaseService ticketPurchaseService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private TicketStockRepository ticketStockRepository;

    @Autowired
    private RedisTemplate<String, String> queueRedisTemplate;

    @Value("${queue.max-processing-count}")
    private int maxProcessingCount;

    private static final int THREAD_POOL_SIZE = 32;

    private final Set<Long> createdTicketStockIds = ConcurrentHashMap.newKeySet();
    private TicketStockTestHelper testHelper;

    @BeforeEach
    void setUp() {
        testHelper = new TicketStockTestHelper(ticketStockRepository, createdTicketStockIds);
    }

    @AfterEach
    void tearDown() {
        // Redis 대기열 정리
        for (Long ticketStockId : createdTicketStockIds) {
            clearQueue(ticketStockId);
        }
        // DB 정리
        if (!createdTicketStockIds.isEmpty()) {
            ticketStockRepository.deleteAllByIdInBatch(createdTicketStockIds);
            createdTicketStockIds.clear();
        }
    }

    private void clearQueue(Long ticketStockId) {
        String waitingKey = QueueKey.waitingQueue(ticketStockId);
        String processingKey = QueueKey.processingQueue(ticketStockId);
        queueRedisTemplate.delete(waitingKey);
        queueRedisTemplate.delete(processingKey);
    }

    @Nested
    @DisplayName("구매 플로우 테스트")
    class PurchaseFlowTest {

        @DisplayName("대기열 등록 → 구매 성공 → 재고 감소 및 처리열에서 제거")
        @Test
        void purchase_success_flow() {
            // given
            TicketStock ticketStock = testHelper.createTicketStock(100);
            Long ticketStockId = ticketStock.getId();
            String userId = "user-1";

            // 대기열 등록
            queueService.enqueue(ticketStockId, userId);

            // when
            ticketPurchaseService.purchase(ticketStockId, userId, 1);

            // then - 재고 감소 확인
            TicketStock result = testHelper.findTicketStock(ticketStockId);
            assertThat(result.getRemainingQuantity()).isEqualTo(99);

            // then - 처리열에서 제거 확인
            String processingKey = QueueKey.processingQueue(ticketStockId);
            Boolean isInProcessing = queueRedisTemplate.opsForSet().isMember(processingKey, userId);
            assertThat(isInProcessing).isFalse();
        }

        @DisplayName("대기열 미등록 상태에서 구매 시도 → QueueAccessDeniedException")
        @Test
        void purchase_without_queue_throws_exception() {
            // given
            TicketStock ticketStock = testHelper.createTicketStock(100);
            Long ticketStockId = ticketStock.getId();
            String userId = "user-1";

            // 대기열 등록하지 않음

            // when & then
            assertThatThrownBy(() -> ticketPurchaseService.purchase(ticketStockId, userId, 1))
                    .isInstanceOf(QueueAccessDeniedException.class);

            // 재고는 그대로
            TicketStock result = testHelper.findTicketStock(ticketStockId);
            assertThat(result.getRemainingQuantity()).isEqualTo(100);
        }

        @DisplayName("구매 실패(재고 부족)해도 처리열에서 제거됨")
        @Test
        void purchase_fail_still_removes_from_processing() {
            // given
            TicketStock ticketStock = testHelper.createTicketStock(1);
            Long ticketStockId = ticketStock.getId();
            String userId = "user-1";

            queueService.enqueue(ticketStockId, userId);

            // when - 재고보다 많이 요청
            assertThatThrownBy(() -> ticketPurchaseService.purchase(ticketStockId, userId, 10))
                    .isInstanceOf(InsufficientTicketStockException.class);

            // then - 처리열에서 제거 확인 (finally 블록 동작 검증)
            String processingKey = QueueKey.processingQueue(ticketStockId);
            Boolean isInProcessing = queueRedisTemplate.opsForSet().isMember(processingKey, userId);
            assertThat(isInProcessing).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class ConcurrencyTest {

        @DisplayName("100개 재고에 100명 동시 구매 → 재고 0, 모두 성공")
        @Test
        void concurrent_purchase_100_users_all_success() throws InterruptedException {
            // given
            int initialQuantity = 100;
            int threadCount = 100;

            TicketStock ticketStock = testHelper.createTicketStock(initialQuantity);
            Long ticketStockId = ticketStock.getId();

            // 100명 대기열 등록
            for (int i = 0; i < threadCount; i++) {
                queueService.enqueue(ticketStockId, "user-" + i);
            }

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final String userId = "user-" + i;
                executorService.submit(() -> {
                    try {
                        ticketPurchaseService.purchase(ticketStockId, userId, 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            TicketStock result = testHelper.findTicketStock(ticketStockId);
            assertThat(result.getRemainingQuantity()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(failCount.get()).isEqualTo(0);

            // 처리열이 비어있는지 확인
            String processingKey = QueueKey.processingQueue(ticketStockId);
            Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);
            assertThat(processingCount).isEqualTo(0);
        }

        @DisplayName("50개 재고에 100명 동시 구매 → 50명 성공, 50명 실패")
        @Test
        void concurrent_purchase_50_stocks_100_users() throws InterruptedException {
            // given
            int initialQuantity = 50;
            int threadCount = 100;

            TicketStock ticketStock = testHelper.createTicketStock(initialQuantity);
            Long ticketStockId = ticketStock.getId();

            // 100명 대기열 등록
            for (int i = 0; i < threadCount; i++) {
                queueService.enqueue(ticketStockId, "user-" + i);
            }

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger insufficientStockCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final String userId = "user-" + i;
                executorService.submit(() -> {
                    try {
                        ticketPurchaseService.purchase(ticketStockId, userId, 1);
                        successCount.incrementAndGet();
                    } catch (InsufficientTicketStockException e) {
                        insufficientStockCount.incrementAndGet();
                    } catch (Exception e) {
                        // 기타 예외
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            TicketStock result = testHelper.findTicketStock(ticketStockId);
            assertThat(result.getRemainingQuantity()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(initialQuantity);
            assertThat(insufficientStockCount.get()).isEqualTo(threadCount - initialQuantity);

            // 처리열이 비어있는지 확인
            String processingKey = QueueKey.processingQueue(ticketStockId);
            Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);
            assertThat(processingCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("대기열 검증 테스트")
    class QueueValidationTest {

        @DisplayName("처리열이 가득 찬 상태에서 대기 중인 사용자는 구매 불가")
        @Test
        void cannot_purchase_when_processing_full() {
            // given
            TicketStock ticketStock = testHelper.createTicketStock(200);
            Long ticketStockId = ticketStock.getId();

            // maxProcessingCount 명을 처리열에 추가 (enter만 하고 complete 안 함)
            for (int i = 0; i < maxProcessingCount; i++) {
                String userId = "processing-" + i;
                queueService.enqueue(ticketStockId, userId);
                queueService.enter(ticketStockId, userId);
            }

            // 새로운 사용자 대기열에 등록
            String newUserId = "waiting-user";
            queueService.enqueue(ticketStockId, newUserId);

            // when & then - 대기 중인 사용자는 구매 불가
            assertThatThrownBy(() -> ticketPurchaseService.purchase(ticketStockId, newUserId, 1))
                    .isInstanceOf(QueueAccessDeniedException.class);

            // 재고는 그대로
            TicketStock result = testHelper.findTicketStock(ticketStockId);
            assertThat(result.getRemainingQuantity()).isEqualTo(200);
        }
    }
}
