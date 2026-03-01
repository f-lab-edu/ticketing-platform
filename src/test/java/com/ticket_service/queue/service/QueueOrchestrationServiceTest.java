package com.ticket_service.queue.service;

import com.ticket_service.queue.service.dto.QueueEventType;
import com.ticket_service.queue.service.dto.QueuePositionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class QueueOrchestrationServiceTest {

    @Mock
    private QueueService queueService;

    @Mock
    private SseEmitterService sseEmitterService;

    @Mock
    private QueueEventPublisher queueEventPublisher;

    @InjectMocks
    private QueueOrchestrationService queueOrchestrationService;

    private static final Long CONCERT_ID = 1L;
    private static final String USER_ID = "user-1";

    @Nested
    @DisplayName("registerAndSubscribe 메서드")
    class RegisterAndSubscribeTest {

        @DisplayName("처리열에 여유가 있으면 1명 입장 처리 후 Redis Pub/Sub으로 입장 이벤트를 발행한다")
        @Test
        void registerAndSubscribe_with_capacity() {
            // given
            SseEmitter mockEmitter = new SseEmitter();
            given(sseEmitterService.createEmitter(CONCERT_ID, USER_ID)).willReturn(mockEmitter);
            given(queueService.enterWaitingQueue(CONCERT_ID, USER_ID)).willReturn(0L);
            given(queueService.hasProcessingCapacity(CONCERT_ID)).willReturn(true);
            given(queueService.permitProcessing(CONCERT_ID)).willReturn(List.of(USER_ID));

            // when
            SseEmitter result = queueOrchestrationService.registerAndSubscribe(CONCERT_ID, USER_ID);

            // then
            assertThat(result).isSameAs(mockEmitter);

            InOrder inOrder = inOrder(queueService, sseEmitterService, queueEventPublisher);
            inOrder.verify(sseEmitterService).createEmitter(CONCERT_ID, USER_ID);
            inOrder.verify(queueService).enterWaitingQueue(CONCERT_ID, USER_ID);
            inOrder.verify(queueService).hasProcessingCapacity(CONCERT_ID);
            inOrder.verify(queueService).permitProcessing(CONCERT_ID);
            inOrder.verify(queueEventPublisher).publishEnterEvent(CONCERT_ID, USER_ID);
        }

        @DisplayName("처리열이 가득 차면 대기 순번 이벤트만 전송한다")
        @Test
        void registerAndSubscribe_without_capacity() {
            // given
            Long position = 5L;
            given(queueService.enterWaitingQueue(CONCERT_ID, USER_ID)).willReturn(position);
            SseEmitter mockEmitter = new SseEmitter();
            given(sseEmitterService.createEmitter(CONCERT_ID, USER_ID)).willReturn(mockEmitter);
            given(queueService.hasProcessingCapacity(CONCERT_ID)).willReturn(false);

            // when
            SseEmitter result = queueOrchestrationService.registerAndSubscribe(CONCERT_ID, USER_ID);

            // then
            assertThat(result).isSameAs(mockEmitter);
            verify(sseEmitterService).sendEventAsync(eq(CONCERT_ID), eq(USER_ID),
                    eq(QueueEventType.QUEUE_POSITION), any(QueuePositionEvent.class));
        }
    }

    @Nested
    @DisplayName("onPurchaseComplete 메서드")
    class OnPurchaseCompleteTest {

        @DisplayName("구매 완료 시 complete → 다음 대기자 입장 이벤트 발행")
        @Test
        void onPurchaseComplete_success() {
            // given
            given(queueService.permitProcessing(CONCERT_ID)).willReturn(List.of("user-2"));

            // when
            queueOrchestrationService.onPurchaseComplete(CONCERT_ID, USER_ID);

            // then
            InOrder inOrder = inOrder(queueService, queueEventPublisher);
            inOrder.verify(queueService).completeProcessing(CONCERT_ID, USER_ID);
            inOrder.verify(queueService).permitProcessing(CONCERT_ID);
            inOrder.verify(queueEventPublisher).publishEnterEvent(CONCERT_ID, "user-2");
        }
    }

    @Nested
    @DisplayName("onCancel 메서드")
    class OnCancelTest {

        @DisplayName("취소 시 dequeue → SSE 종료 → 다음 대기자 입장 이벤트 발행")
        @Test
        void onCancel_success() {
            // given
            given(queueService.permitProcessing(CONCERT_ID)).willReturn(List.of("user-2"));

            // when
            queueOrchestrationService.onCancel(CONCERT_ID, USER_ID);

            // then
            InOrder inOrder = inOrder(queueService, sseEmitterService, queueEventPublisher);
            inOrder.verify(queueService).removeFromQueue(CONCERT_ID, USER_ID);
            inOrder.verify(sseEmitterService).completeEmitter(CONCERT_ID, USER_ID);
            inOrder.verify(queueService).permitProcessing(CONCERT_ID);
            inOrder.verify(queueEventPublisher).publishEnterEvent(CONCERT_ID, "user-2");
        }
    }
}
