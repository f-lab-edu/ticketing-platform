package com.ticket_service.queue.service;

import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.domain.QueueInfo;
import com.ticket_service.queue.domain.QueueStatus;
import com.ticket_service.queue.exception.AlreadyInQueueException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisQueueServiceTest {

    @Mock
    private RedisTemplate<String, String> queueRedisTemplate;

    @Mock
    private RedissonLockTemplate redissonLockTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisQueueService redisQueueService;

    private static final Long TICKET_STOCK_ID = 1L;
    private static final String USER_ID = "user-1";
    private static final String WAITING_KEY = "QUEUE:WAITING:1";
    private static final String PROCESSING_KEY = "QUEUE:PROCESSING:1";

    @BeforeEach
    void setUp() {
        redisQueueService = new RedisQueueService(queueRedisTemplate, redissonLockTemplate);
        ReflectionTestUtils.setField(redisQueueService, "maxProcessingCount", 100);
        ReflectionTestUtils.setField(redisQueueService, "entryTimeoutSeconds", 300L);
        ReflectionTestUtils.setField(redisQueueService, "waitingTimeoutSeconds", 1800L);

        given(queueRedisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(queueRedisTemplate.opsForSet()).willReturn(setOperations);

        // RedissonLockTemplate mock 설정: 락 없이 바로 실행
        given(redissonLockTemplate.executeWithLock(anyString(), any(Supplier.class)))
                .willAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
        willAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).given(redissonLockTemplate).executeWithLock(anyString(), any(Runnable.class));
    }

    @Nested
    @DisplayName("enqueue 메서드")
    class EnqueueTest {

        @DisplayName("대기열 등록 성공 - 첫 번째 사용자")
        @Test
        void enqueue_success_first_user() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(zSetOperations.score(WAITING_KEY, USER_ID)).willReturn(null);
            given(zSetOperations.add(eq(WAITING_KEY), eq(USER_ID), anyDouble())).willReturn(true);
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(0L);

            // when
            Long position = redisQueueService.enqueue(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(position).isEqualTo(0L);
            verify(zSetOperations).add(eq(WAITING_KEY), eq(USER_ID), anyDouble());
        }

        @DisplayName("대기열 등록 실패 - 이미 처리중인 사용자")
        @Test
        void enqueue_fail_already_processing() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> redisQueueService.enqueue(TICKET_STOCK_ID, USER_ID))
                    .isInstanceOf(AlreadyInQueueException.class)
                    .hasMessage("이미 입장한 사용자입니다.");
        }

        @DisplayName("대기열 등록 실패 - 이미 대기열에 등록된 사용자")
        @Test
        void enqueue_fail_already_in_waiting_queue() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(zSetOperations.score(WAITING_KEY, USER_ID)).willReturn(1000.0);

            // when & then
            assertThatThrownBy(() -> redisQueueService.enqueue(TICKET_STOCK_ID, USER_ID))
                    .isInstanceOf(AlreadyInQueueException.class)
                    .hasMessage("이미 대기열에 등록된 사용자입니다.");
        }
    }

    @Nested
    @DisplayName("getPosition 메서드")
    class GetPositionTest {

        @DisplayName("순번 조회 성공 - 대기열에 있는 경우")
        @Test
        void getPosition_success() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(5L);

            // when
            Long position = redisQueueService.getPosition(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(position).isEqualTo(5L);
        }

        @DisplayName("순번 조회 - 대기열에 없는 경우 null 반환")
        @Test
        void getPosition_not_in_queue() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(null);

            // when
            Long position = redisQueueService.getPosition(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(position).isNull();
        }
    }

    @Nested
    @DisplayName("canEnter 메서드")
    class CanEnterTest {

        @DisplayName("입장 가능 - 이미 처리중인 사용자")
        @Test
        void canEnter_true_already_processing() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(true);

            // when
            boolean canEnter = redisQueueService.canEnter(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(canEnter).isTrue();
        }

        @DisplayName("입장 가능 - 대기 순번이 허용 범위 내")
        @Test
        void canEnter_true_within_available_slots() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(50L);
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(0L);

            // when
            boolean canEnter = redisQueueService.canEnter(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(canEnter).isTrue();
        }

        @DisplayName("입장 불가 - 대기 순번이 허용 범위 초과")
        @Test
        void canEnter_false_exceed_available_slots() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(100L);
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(0L);

            // when
            boolean canEnter = redisQueueService.canEnter(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(canEnter).isFalse();
        }

        @DisplayName("입장 불가 - 대기열에 없는 사용자")
        @Test
        void canEnter_false_not_in_queue() {
            // given
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(0L);
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(null);

            // when
            boolean canEnter = redisQueueService.canEnter(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(canEnter).isFalse();
        }
    }

    @Nested
    @DisplayName("enter 메서드")
    class EnterTest {

        @DisplayName("입장 처리 성공 - 대기열에서 제거 후 처리중으로 이동")
        @Test
        void enter_success() {
            // when
            redisQueueService.enter(TICKET_STOCK_ID, USER_ID);

            // then
            verify(zSetOperations).remove(WAITING_KEY, USER_ID);
            verify(setOperations).add(PROCESSING_KEY, USER_ID);
        }
    }

    @Nested
    @DisplayName("complete 메서드")
    class CompleteTest {

        @DisplayName("완료 처리 성공 - 처리중에서 제거")
        @Test
        void complete_success() {
            // when
            redisQueueService.complete(TICKET_STOCK_ID, USER_ID);

            // then
            verify(setOperations).remove(PROCESSING_KEY, USER_ID);
        }
    }

    @Nested
    @DisplayName("dequeue 메서드")
    class DequeueTest {

        @DisplayName("대기열 취소 성공 - 대기열과 처리중 모두에서 제거")
        @Test
        void dequeue_success() {
            // when
            redisQueueService.dequeue(TICKET_STOCK_ID, USER_ID);

            // then
            verify(zSetOperations).remove(WAITING_KEY, USER_ID);
            verify(setOperations).remove(PROCESSING_KEY, USER_ID);
        }
    }

    @Nested
    @DisplayName("getQueueInfo 메서드")
    class GetQueueInfoTest {

        @DisplayName("대기 상태 - WAITING")
        @Test
        void getQueueInfo_waiting() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(50L);
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(100L);

            // when
            QueueInfo queueInfo = redisQueueService.getQueueInfo(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(queueInfo.getPosition()).isEqualTo(50L);
            assertThat(queueInfo.isCanEnter()).isFalse();
            assertThat(queueInfo.getStatus()).isEqualTo(QueueStatus.WAITING);
        }

        @DisplayName("입장 가능 상태 - CAN_ENTER")
        @Test
        void getQueueInfo_can_enter() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(0L);
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(50L);

            // when
            QueueInfo queueInfo = redisQueueService.getQueueInfo(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(queueInfo.getPosition()).isEqualTo(0L);
            assertThat(queueInfo.isCanEnter()).isTrue();
            assertThat(queueInfo.getStatus()).isEqualTo(QueueStatus.CAN_ENTER);
        }

        @DisplayName("처리중 상태 - PROCESSING")
        @Test
        void getQueueInfo_processing() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(null);
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(true);

            // when
            QueueInfo queueInfo = redisQueueService.getQueueInfo(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(queueInfo.getPosition()).isNull();
            assertThat(queueInfo.isCanEnter()).isTrue();
            assertThat(queueInfo.getStatus()).isEqualTo(QueueStatus.PROCESSING);
        }

        @DisplayName("대기열에 없는 상태 - NOT_IN_QUEUE")
        @Test
        void getQueueInfo_not_in_queue() {
            // given
            given(zSetOperations.rank(WAITING_KEY, USER_ID)).willReturn(null);
            given(setOperations.isMember(PROCESSING_KEY, USER_ID)).willReturn(false);
            given(setOperations.size(PROCESSING_KEY)).willReturn(0L);

            // when
            QueueInfo queueInfo = redisQueueService.getQueueInfo(TICKET_STOCK_ID, USER_ID);

            // then
            assertThat(queueInfo.getPosition()).isNull();
            assertThat(queueInfo.isCanEnter()).isFalse();
            assertThat(queueInfo.getStatus()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }
    }
}
