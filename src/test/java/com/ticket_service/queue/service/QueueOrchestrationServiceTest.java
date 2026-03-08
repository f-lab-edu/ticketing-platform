package com.ticket_service.queue.service;

import com.ticket_service.auth.service.QueueTokenService;
import com.ticket_service.common.redis.RedissonLockTemplate;
import com.ticket_service.queue.service.dto.QueueEventType;
import com.ticket_service.queue.service.dto.QueuePositionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QueueOrchestrationServiceTest {

    @Mock
    private WaitingQueue waitingQueue;

    @Mock
    private ProcessingCounter processingCounter;

    @Mock
    private QueueTokenService queueTokenService;

    @Mock
    private SseEmitterService sseEmitterService;

    @Mock
    private QueueEventPublisher queueEventPublisher;

    @Mock
    private RedissonLockTemplate redissonLockTemplate;

    private QueueOrchestrationService queueOrchestrationService;

    private static final Long CONCERT_ID = 1L;
    private static final String USER_ID = "user-1";
    private static final String TOKEN = "test-token";

    @BeforeEach
    void setUp() {
        queueOrchestrationService = new QueueOrchestrationService(
                waitingQueue,
                processingCounter,
                queueTokenService,
                sseEmitterService,
                queueEventPublisher,
                redissonLockTemplate
        );

        given(redissonLockTemplate.executeWithLock(anyString(), any(Supplier.class)))
                .willAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(1);
                    return supplier.get();
                });
    }

    @Nested
    @DisplayName("registerAndSubscribe 메서드")
    class RegisterAndSubscribeTest {

        @DisplayName("처리열에 여유가 있으면 입장 처리 후 토큰과 함께 이벤트 발행")
        @Test
        void registerAndSubscribe_with_capacity() {
            // given
            SseEmitter mockEmitter = new SseEmitter();
            given(sseEmitterService.createEmitter(CONCERT_ID, USER_ID)).willReturn(mockEmitter);
            given(waitingQueue.rank(CONCERT_ID, USER_ID)).willReturn(0L);
            given(processingCounter.remainingCapacity(CONCERT_ID)).willReturn(10);
            given(waitingQueue.pollTopUsersWithWaitingTime(CONCERT_ID, 10))
                    .willReturn(List.of(new PolledUser(USER_ID, 1000L)));
            given(queueTokenService.generateToken(CONCERT_ID, USER_ID)).willReturn(TOKEN);

            // when
            SseEmitter result = queueOrchestrationService.registerAndSubscribe(CONCERT_ID, USER_ID);

            // then
            assertThat(result).isSameAs(mockEmitter);
            verify(waitingQueue).add(CONCERT_ID, USER_ID);
            verify(processingCounter).incrementBy(CONCERT_ID, 1);
            verify(queueEventPublisher).publishEnterEvent(CONCERT_ID, USER_ID, TOKEN);
        }

        @DisplayName("처리열이 가득 차면 대기 순번 이벤트만 전송한다")
        @Test
        void registerAndSubscribe_without_capacity() {
            // given
            Long position = 5L;
            SseEmitter mockEmitter = new SseEmitter();
            given(sseEmitterService.createEmitter(CONCERT_ID, USER_ID)).willReturn(mockEmitter);
            given(waitingQueue.rank(CONCERT_ID, USER_ID)).willReturn(position);
            given(processingCounter.remainingCapacity(CONCERT_ID)).willReturn(0);

            // when
            SseEmitter result = queueOrchestrationService.registerAndSubscribe(CONCERT_ID, USER_ID);

            // then
            assertThat(result).isSameAs(mockEmitter);
            verify(sseEmitterService).sendEventAsync(eq(CONCERT_ID), eq(USER_ID),
                    eq(QueueEventType.QUEUE_POSITION), any(QueuePositionEvent.class));
        }
    }

    @Nested
    @DisplayName("onReservationComplete 메서드")
    class OnReservationCompleteTest {

        @DisplayName("예약 완료 시 카운터 감소 → 다음 대기자 입장 이벤트 발행")
        @Test
        void onReservationComplete_success() {
            // given
            given(processingCounter.remainingCapacity(CONCERT_ID)).willReturn(1);
            given(waitingQueue.pollTopUsersWithWaitingTime(CONCERT_ID, 1))
                    .willReturn(List.of(new PolledUser("user-2", 2000L)));
            given(queueTokenService.generateToken(CONCERT_ID, "user-2")).willReturn("token-2");

            // when
            queueOrchestrationService.onReservationComplete(CONCERT_ID, USER_ID);

            // then
            InOrder inOrder = inOrder(processingCounter, queueEventPublisher);
            inOrder.verify(processingCounter).decrement(CONCERT_ID);
            inOrder.verify(processingCounter).incrementBy(CONCERT_ID, 1);
            inOrder.verify(queueEventPublisher).publishEnterEvent(CONCERT_ID, "user-2", "token-2");
        }
    }

    @Nested
    @DisplayName("onWaitingCancel 메서드")
    class OnWaitingCancelTest {

        @DisplayName("대기열 취소 시 대기열 제거 → SSE 종료 (카운터 감소 없음)")
        @Test
        void onWaitingCancel_success() {
            // when
            queueOrchestrationService.onWaitingCancel(CONCERT_ID, USER_ID);

            // then - 대기열 제거와 SSE 종료만 수행
            InOrder inOrder = inOrder(waitingQueue, sseEmitterService);
            inOrder.verify(waitingQueue).remove(CONCERT_ID, USER_ID);
            inOrder.verify(sseEmitterService).completeEmitter(CONCERT_ID, USER_ID);

            // 카운터 감소는 호출되지 않아야 함 (아직 입장 전이므로)
            verify(processingCounter, never()).decrement(CONCERT_ID);
        }
    }
}
