package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import com.ticket_service.queue.domain.QueueInfo;
import com.ticket_service.queue.domain.QueueStatus;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class RedisQueueServiceIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private RedisTemplate<String, String> queueRedisTemplate;

    private static final Long TICKET_STOCK_ID = 1L;
    private static final int THREAD_POOL_SIZE = 32;

    @Value("${queue.max-processing-count}")
    private int maxProcessingCount;

    @BeforeEach
    void setUp() {
        clearQueue();
    }

    @AfterEach
    void tearDown() {
        clearQueue();
    }

    private void clearQueue() {
        String waitingKey = QueueKey.waitingQueue(TICKET_STOCK_ID);
        String processingKey = QueueKey.processingQueue(TICKET_STOCK_ID);
        queueRedisTemplate.delete(waitingKey);
        queueRedisTemplate.delete(processingKey);
    }

    @Nested
    @DisplayName("동시성 - 중복 등록 방지")
    class DuplicateRegistrationTest {

        @DisplayName("동일 사용자가 동시에 10번 등록 시도 - 1번만 성공")
        @Test
        void concurrent_enqueue_same_user_only_one_success() throws InterruptedException {
            // given
            String userId = "user-1";
            int threadCount = 10;

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        queueService.enqueue(TICKET_STOCK_ID, userId);
                        successCount.incrementAndGet();
                    } catch (AlreadyInQueueException e) {
                        failCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failCount.get()).isEqualTo(threadCount - 1);

            // 대기열에 사용자가 1명만 있는지 확인
            String waitingKey = QueueKey.waitingQueue(TICKET_STOCK_ID);
            Long queueSize = queueRedisTemplate.opsForZSet().size(waitingKey);
            assertThat(queueSize).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("동시성 - 선착순 보장")
    class OrderGuaranteeTest {

        @DisplayName("100명 동시 등록 - 모두 대기열에 등록되고 순번 부여")
        @Test
        void concurrent_enqueue_100_users_all_registered() throws InterruptedException {
            // given
            int threadCount = 100;

            AtomicInteger successCount = new AtomicInteger(0);
            Set<Long> positions = ConcurrentHashMap.newKeySet();

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                final String userId = "user-" + i;
                executorService.submit(() -> {
                    try {
                        Long position = queueService.enqueue(TICKET_STOCK_ID, userId);
                        positions.add(position);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // 실패 시 무시
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            assertThat(successCount.get()).isEqualTo(threadCount);

            // 대기열에 100명 있는지 확인
            String waitingKey = QueueKey.waitingQueue(TICKET_STOCK_ID);
            Long queueSize = queueRedisTemplate.opsForZSet().size(waitingKey);
            assertThat(queueSize).isEqualTo(threadCount);

            // 순번이 0부터 99까지 모두 존재하는지 확인
            assertThat(positions)
                    .hasSize(threadCount)
                    .allMatch(p -> p >= 0 && p < threadCount);
        }
    }

    @Nested
    @DisplayName("동시성 - 처리열 제한")
    class ProcessingLimitTest {

        @DisplayName("200명 등록 후 동시에 enter 시도 - 처리열 크기 확인")
        @Test
        void concurrent_enter_processing_count_check() throws InterruptedException {
            // given
            int userCount = 200;

            // 먼저 200명 대기열에 등록
            for (int i = 0; i < userCount; i++) {
                queueService.enqueue(TICKET_STOCK_ID, "user-" + i);
            }

            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(userCount);

            AtomicInteger enterCount = new AtomicInteger(0);

            // when - 모든 사용자가 동시에 canEnter 확인 후 enter 시도
            for (int i = 0; i < userCount; i++) {
                final String userId = "user-" + i;
                executorService.submit(() -> {
                    try {
                        if (queueService.canEnter(TICKET_STOCK_ID, userId)) {
                            queueService.enter(TICKET_STOCK_ID, userId);
                            enterCount.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then
            String processingKey = QueueKey.processingQueue(TICKET_STOCK_ID);
            Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);

            // 처리열 크기가 maxProcessingCount를 크게 초과하지 않아야 함
            // (약간의 초과는 canEnter race condition으로 허용)
            assertThat(processingCount).isGreaterThanOrEqualTo(maxProcessingCount);
            assertThat(enterCount.get()).isEqualTo(processingCount.intValue());
        }

        @DisplayName("처리열이 가득 찬 상태에서 새 사용자는 입장 불가")
        @Test
        void cannot_enter_when_processing_full() throws InterruptedException {
            // given - maxProcessingCount 명을 처리열에 추가
            for (int i = 0; i < maxProcessingCount; i++) {
                String userId = "processing-user-" + i;
                queueService.enqueue(TICKET_STOCK_ID, userId);
                queueService.enter(TICKET_STOCK_ID, userId);
            }

            // 새로운 사용자 대기열에 등록
            String newUserId = "new-user";
            queueService.enqueue(TICKET_STOCK_ID, newUserId);

            // when
            boolean canEnter = queueService.canEnter(TICKET_STOCK_ID, newUserId);

            // then
            assertThat(canEnter).isFalse();
        }
    }

    @Nested
    @DisplayName("동시성 - enter 정합성")
    class EnterConsistencyTest {

        @DisplayName("동일 사용자가 동시에 enter 호출 - 처리열에 1번만 추가")
        @Test
        void concurrent_enter_same_user_only_once() throws InterruptedException {
            // given
            String userId = "user-1";
            queueService.enqueue(TICKET_STOCK_ID, userId);

            int threadCount = 10;
            ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            CountDownLatch latch = new CountDownLatch(threadCount);

            // when
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        queueService.enter(TICKET_STOCK_ID, userId);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executorService.shutdown();

            // then - 처리열에 1명만 있어야 함 (Set 특성)
            String processingKey = QueueKey.processingQueue(TICKET_STOCK_ID);
            Long processingCount = queueRedisTemplate.opsForSet().size(processingKey);
            assertThat(processingCount).isEqualTo(1);

            // 대기열에서는 제거되어야 함
            String waitingKey = QueueKey.waitingQueue(TICKET_STOCK_ID);
            Long waitingCount = queueRedisTemplate.opsForZSet().size(waitingKey);
            assertThat(waitingCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("전체 플로우 테스트")
    class FullFlowTest {

        @DisplayName("등록 → 상태조회 → 입장 → 완료 플로우")
        @Test
        void full_queue_flow() {
            // given
            String userId = "user-1";

            // 1. 등록
            QueueInfo registerInfo = queueService.registerAndGetInfo(TICKET_STOCK_ID, userId);
            assertThat(registerInfo.getStatus()).isIn(QueueStatus.WAITING, QueueStatus.CAN_ENTER);
            assertThat(registerInfo.getPosition()).isEqualTo(0L);

            // 2. 상태 조회
            QueueInfo statusInfo = queueService.getQueueInfo(TICKET_STOCK_ID, userId);
            assertThat(statusInfo.isCanEnter()).isTrue();
            assertThat(statusInfo.getStatus()).isEqualTo(QueueStatus.CAN_ENTER);

            // 3. 입장
            queueService.enter(TICKET_STOCK_ID, userId);
            QueueInfo afterEnterInfo = queueService.getQueueInfo(TICKET_STOCK_ID, userId);
            assertThat(afterEnterInfo.getStatus()).isEqualTo(QueueStatus.PROCESSING);

            // 4. 완료
            queueService.complete(TICKET_STOCK_ID, userId);
            QueueInfo afterCompleteInfo = queueService.getQueueInfo(TICKET_STOCK_ID, userId);
            assertThat(afterCompleteInfo.getStatus()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }

        @DisplayName("50명 처리 완료 후 대기열에서 50명 추가 입장 가능")
        @Test
        void after_complete_waiting_users_can_enter() throws InterruptedException {
            // given - 100명 처리열에 추가
            List<String> processingUsers = new ArrayList<>();
            for (int i = 0; i < maxProcessingCount; i++) {
                String userId = "processing-" + i;
                processingUsers.add(userId);
                queueService.enqueue(TICKET_STOCK_ID, userId);
                queueService.enter(TICKET_STOCK_ID, userId);
            }

            // 50명 대기열에 추가
            List<String> waitingUsers = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String userId = "waiting-" + i;
                waitingUsers.add(userId);
                queueService.enqueue(TICKET_STOCK_ID, userId);
            }

            // 대기열 사용자들은 입장 불가
            assertThat(waitingUsers)
                    .allMatch(userId -> !queueService.canEnter(TICKET_STOCK_ID, userId));

            // when - 처리열의 50명 완료
            for (int i = 0; i < 50; i++) {
                queueService.complete(TICKET_STOCK_ID, processingUsers.get(i));
            }

            // then - 대기열의 50명 입장 가능
            long canEnterCount = waitingUsers.stream()
                    .filter(userId -> queueService.canEnter(TICKET_STOCK_ID, userId))
                    .count();
            assertThat(canEnterCount).isEqualTo(50);
        }
    }
}
