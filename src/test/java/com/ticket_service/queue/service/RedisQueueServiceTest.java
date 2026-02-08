package com.ticket_service.queue.service;

import com.ticket_service.common.redis.RedissonLockTemplate;
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
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisQueueServiceTest {

    @Mock
    private WaitingQueue waitingQueue;

    @Mock
    private ProcessingSet processingSet;

    @Mock
    private RedissonLockTemplate redissonLockTemplate;

    private RedisQueueService redisQueueService;

    private static final Long CONCERT_ID = 1L;
    private static final String USER_ID = "user-1";

    @BeforeEach
    void setUp() {
        redisQueueService = new RedisQueueService(waitingQueue, processingSet, redissonLockTemplate);

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
            given(processingSet.contains(CONCERT_ID, USER_ID)).willReturn(false);
            given(waitingQueue.contains(CONCERT_ID, USER_ID)).willReturn(false);
            given(waitingQueue.rank(CONCERT_ID, USER_ID)).willReturn(0L);

            Long position = redisQueueService.enterWaitingQueue(CONCERT_ID, USER_ID);

            assertThat(position).isEqualTo(0L);
            verify(waitingQueue).add(CONCERT_ID, USER_ID);
        }

        @DisplayName("대기열 등록 실패 - 이미 처리중인 사용자")
        @Test
        void enqueue_fail_already_processing() {
            given(processingSet.contains(CONCERT_ID, USER_ID)).willReturn(true);

            assertThatThrownBy(() -> redisQueueService.enterWaitingQueue(CONCERT_ID, USER_ID))
                    .isInstanceOf(AlreadyInQueueException.class)
                    .hasMessage("이미 입장한 사용자입니다.");
        }

        @DisplayName("대기열 등록 실패 - 이미 대기열에 등록된 사용자")
        @Test
        void enqueue_fail_already_in_waiting_queue() {
            given(processingSet.contains(CONCERT_ID, USER_ID)).willReturn(false);
            given(waitingQueue.contains(CONCERT_ID, USER_ID)).willReturn(true);

            assertThatThrownBy(() -> redisQueueService.enterWaitingQueue(CONCERT_ID, USER_ID))
                    .isInstanceOf(AlreadyInQueueException.class)
                    .hasMessage("이미 대기열에 등록된 사용자입니다.");
        }
    }

    @Nested
    @DisplayName("complete 메서드")
    class CompleteTest {

        @DisplayName("완료 처리 성공 - 처리중에서 제거")
        @Test
        void complete_success() {
            redisQueueService.completeProcessing(CONCERT_ID, USER_ID);

            verify(processingSet).remove(CONCERT_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("dequeue 메서드")
    class DequeueTest {

        @DisplayName("대기열 취소 성공 - 대기열과 처리중 모두에서 제거")
        @Test
        void dequeue_success() {
            redisQueueService.removeFromQueue(CONCERT_ID, USER_ID);

            verify(waitingQueue).remove(CONCERT_ID, USER_ID);
            verify(processingSet).remove(CONCERT_ID, USER_ID);
        }
    }

    @Nested
    @DisplayName("isInprocessingSet 메서드")
    class IsInprocessingSetTest {

        @DisplayName("처리열에 있는 사용자 - true 반환")
        @Test
        void isInprocessingSet_true() {
            given(processingSet.contains(CONCERT_ID, USER_ID)).willReturn(true);

            boolean result = redisQueueService.isInProcessing(CONCERT_ID, USER_ID);

            assertThat(result).isTrue();
        }

        @DisplayName("처리열에 없는 사용자 - false 반환")
        @Test
        void isInprocessingSet_false() {
            given(processingSet.contains(CONCERT_ID, USER_ID)).willReturn(false);

            boolean result = redisQueueService.isInProcessing(CONCERT_ID, USER_ID);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("enterNextUsers 메서드")
    class EnterNextUsersTest {

        @DisplayName("가용 슬롯이 있고 대기자가 있으면 입장시킴")
        @Test
        void enterNextUsers_success() {
            given(processingSet.remainingCapacity(CONCERT_ID)).willReturn(5L);
            given(waitingQueue.pollTopUsers(CONCERT_ID, 5)).willReturn(List.of("user-1", "user-2", "user-3"));

            List<String> enteredUsers = redisQueueService.permitProcessing(CONCERT_ID);

            assertThat(enteredUsers).containsExactly("user-1", "user-2", "user-3");
            verify(processingSet).addAll(CONCERT_ID, List.of("user-1", "user-2", "user-3"));
        }

        @DisplayName("가용 슬롯이 없으면 빈 목록 반환")
        @Test
        void enterNextUsers_no_available_slots() {
            given(processingSet.remainingCapacity(CONCERT_ID)).willReturn(0L);

            List<String> enteredUsers = redisQueueService.permitProcessing(CONCERT_ID);

            assertThat(enteredUsers).isEmpty();
            verify(waitingQueue, never()).pollTopUsers(anyLong(), anyLong());
        }

        @DisplayName("대기열이 비어있으면 빈 목록 반환")
        @Test
        void enterNextUsers_empty_waiting_queue() {
            given(processingSet.remainingCapacity(CONCERT_ID)).willReturn(100L);
            given(waitingQueue.pollTopUsers(CONCERT_ID, 100)).willReturn(List.of());

            List<String> enteredUsers = redisQueueService.permitProcessing(CONCERT_ID);

            assertThat(enteredUsers).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPosition 메서드")
    class GetPositionTest {

        @DisplayName("순번 조회 성공 - 대기열에 있는 경우")
        @Test
        void getPosition_success() {
            given(waitingQueue.rank(CONCERT_ID, USER_ID)).willReturn(5L);

            Long position = redisQueueService.getPosition(CONCERT_ID, USER_ID);

            assertThat(position).isEqualTo(5L);
        }

        @DisplayName("순번 조회 - 대기열에 없는 경우 null 반환")
        @Test
        void getPosition_not_in_queue() {
            given(waitingQueue.rank(CONCERT_ID, USER_ID)).willReturn(null);

            Long position = redisQueueService.getPosition(CONCERT_ID, USER_ID);

            assertThat(position).isNull();
        }
    }
}
