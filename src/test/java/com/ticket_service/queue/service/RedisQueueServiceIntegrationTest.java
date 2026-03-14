package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
    private ProcessingCounter processingCounter;

    @Autowired
    private RedisTemplate<String, String> queueRedisTemplate;

    private static final Long CONCERT_ID = 1L;
    private static final int THREAD_POOL_SIZE = 32;

    @Value("${processing.max-count}")
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
        String waitingKey = QueueKey.waitingQueue(CONCERT_ID);
        String counterKey = QueueKey.processingCounter(CONCERT_ID);
        queueRedisTemplate.delete(waitingKey);
        queueRedisTemplate.delete(counterKey);
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
                        queueService.enterWaitingQueue(CONCERT_ID, userId);
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
            String waitingKey = QueueKey.waitingQueue(CONCERT_ID);
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
                        Long position = queueService.enterWaitingQueue(CONCERT_ID, userId);
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
            String waitingKey = QueueKey.waitingQueue(CONCERT_ID);
            Long queueSize = queueRedisTemplate.opsForZSet().size(waitingKey);
            assertThat(queueSize).isEqualTo(threadCount);

            // 순번이 0부터 99까지 모두 존재하는지 확인
            assertThat(positions)
                    .hasSize(threadCount)
                    .allMatch(p -> p >= 0 && p < threadCount);
        }
    }

    @Nested
    @DisplayName("enterNextUsers - 일괄 입장")
    class EnterNextUsersTest {

        @DisplayName("가용 슬롯만큼 대기자를 처리열로 이동")
        @Test
        void enterNextUsers_moves_users_to_processing() {
            // given - 200명 대기열에 등록
            int userCount = 200;
            for (int i = 0; i < userCount; i++) {
                queueService.enterWaitingQueue(CONCERT_ID, "user-" + i);
            }

            // when
            List<String> enteredUsers = queueService.permitProcessing(CONCERT_ID);

            // then - maxProcessingCount만큼 입장
            assertThat(enteredUsers).hasSize(maxProcessingCount);

            // 카운터 확인
            int processingCount = processingCounter.getCount(CONCERT_ID);
            assertThat(processingCount).isEqualTo(maxProcessingCount);

            // 대기열에 나머지 남아있는지 확인
            String waitingKey = QueueKey.waitingQueue(CONCERT_ID);
            Long waitingCount = queueRedisTemplate.opsForZSet().size(waitingKey);
            assertThat(waitingCount).isEqualTo(userCount - maxProcessingCount);
        }

        @DisplayName("처리열이 가득 찬 상태에서 enterNextUsers 호출 시 빈 목록 반환")
        @Test
        void enterNextUsers_no_slots_returns_empty() {
            // given - maxProcessingCount만큼 등록 후 입장
            for (int i = 0; i < maxProcessingCount; i++) {
                queueService.enterWaitingQueue(CONCERT_ID, "user-" + i);
            }
            queueService.permitProcessing(CONCERT_ID);

            // 추가 대기자 등록
            queueService.enterWaitingQueue(CONCERT_ID, "extra-user");

            // when
            List<String> enteredUsers = queueService.permitProcessing(CONCERT_ID);

            // then
            assertThat(enteredUsers).isEmpty();
        }

        @DisplayName("complete 후 enterNextUsers 호출 시 대기자 입장")
        @Test
        void enterNextUsers_after_complete() {
            // given - maxProcessingCount만큼 등록 후 입장
            for (int i = 0; i < maxProcessingCount; i++) {
                queueService.enterWaitingQueue(CONCERT_ID, "user-" + i);
            }
            queueService.permitProcessing(CONCERT_ID);

            // 추가 대기자 50명 등록
            List<String> waitingUsers = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String userId = "waiting-" + i;
                waitingUsers.add(userId);
                queueService.enterWaitingQueue(CONCERT_ID, userId);
            }

            // 처리열의 50명 완료 (카운터 감소)
            for (int i = 0; i < 50; i++) {
                queueService.completeProcessing(CONCERT_ID, "user-" + i);
            }

            // when
            List<String> enteredUsers = queueService.permitProcessing(CONCERT_ID);

            // then - 50명 입장
            assertThat(enteredUsers).hasSize(50);

            // 카운터가 maxProcessingCount인지 확인
            int processingCount = processingCounter.getCount(CONCERT_ID);
            assertThat(processingCount).isEqualTo(maxProcessingCount);
        }
    }

    @Nested
    @DisplayName("처리 카운터 테스트")
    class ProcessingCounterTest {

        @DisplayName("입장 후 카운터 확인")
        @Test
        void counter_after_enter() {
            // given
            String userId = "user-1";
            queueService.enterWaitingQueue(CONCERT_ID, userId);
            queueService.permitProcessing(CONCERT_ID);

            // when
            int count = processingCounter.getCount(CONCERT_ID);

            // then
            assertThat(count).isEqualTo(1);
        }

        @DisplayName("완료 후 카운터 감소")
        @Test
        void counter_after_complete() {
            // given
            String userId = "user-1";
            queueService.enterWaitingQueue(CONCERT_ID, userId);
            queueService.permitProcessing(CONCERT_ID);

            // when
            queueService.completeProcessing(CONCERT_ID, userId);

            // then
            int count = processingCounter.getCount(CONCERT_ID);
            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("전체 플로우 테스트")
    class FullFlowTest {

        @DisplayName("등록 → enterNextUsers → 카운터 확인 → 완료 플로우")
        @Test
        void full_queue_flow() {
            // given
            String userId = "user-1";

            // 1. 등록
            Long position = queueService.enterWaitingQueue(CONCERT_ID, userId);
            assertThat(position).isEqualTo(0L);

            // 2. 입장
            List<String> entered = queueService.permitProcessing(CONCERT_ID);
            assertThat(entered).contains(userId);

            // 3. 카운터 확인
            assertThat(processingCounter.getCount(CONCERT_ID)).isEqualTo(1);

            // 4. 완료
            queueService.completeProcessing(CONCERT_ID, userId);
            assertThat(processingCounter.getCount(CONCERT_ID)).isEqualTo(0);
        }
    }
}
