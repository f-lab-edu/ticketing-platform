package com.ticket_service.ticket.service;

import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueOrchestrationService;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketPurchaseServiceTest {

    @Mock
    private QueueOrchestrationService queueOrchestrationService;

    @Mock
    private TicketStockService ticketStockService;

    @InjectMocks
    private TicketPurchaseService ticketPurchaseService;

    private static final Long CONCERT_ID = 1L;
    private static final String USER_ID = "user-1";
    private static final int QUANTITY = 1;

    @DisplayName("구매 성공 - 처리열 검증 → 차감 → onPurchaseComplete 순서로 실행")
    @Test
    void purchase_success() {
        // given
        given(queueOrchestrationService.isInProcessing(CONCERT_ID, USER_ID)).willReturn(true);

        // when
        ticketPurchaseService.purchase(CONCERT_ID, USER_ID, QUANTITY);

        // then
        InOrder inOrder = inOrder(ticketStockService, queueOrchestrationService);
        inOrder.verify(queueOrchestrationService).isInProcessing(CONCERT_ID, USER_ID);
        inOrder.verify(ticketStockService).decreaseByConcertId(CONCERT_ID, QUANTITY);
        inOrder.verify(queueOrchestrationService).onPurchaseComplete(CONCERT_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 처리열에 없을 때 예외 발생")
    @Test
    void purchase_fail_not_in_processing_queue() {
        // given
        given(queueOrchestrationService.isInProcessing(CONCERT_ID, USER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(CONCERT_ID, USER_ID, QUANTITY))
                .isInstanceOf(QueueAccessDeniedException.class)
                .hasMessage("처리열에 없는 사용자입니다. 대기열을 통해 입장해주세요.");

        verify(queueOrchestrationService).isInProcessing(CONCERT_ID, USER_ID);
        verify(ticketStockService, never()).decreaseByConcertId(CONCERT_ID, QUANTITY);
        verify(queueOrchestrationService, never()).onPurchaseComplete(CONCERT_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 재고 부족해도 onPurchaseComplete 호출됨 (finally 블록)")
    @Test
    void purchase_fail_insufficient_stock_but_complete_called() {
        // given
        given(queueOrchestrationService.isInProcessing(CONCERT_ID, USER_ID)).willReturn(true);
        willThrow(new InsufficientTicketStockException(0, QUANTITY))
                .given(ticketStockService).decreaseByConcertId(CONCERT_ID, QUANTITY);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(CONCERT_ID, USER_ID, QUANTITY))
                .isInstanceOf(InsufficientTicketStockException.class);

        InOrder inOrder = inOrder(ticketStockService, queueOrchestrationService);
        inOrder.verify(queueOrchestrationService).isInProcessing(CONCERT_ID, USER_ID);
        inOrder.verify(ticketStockService).decreaseByConcertId(CONCERT_ID, QUANTITY);
        inOrder.verify(queueOrchestrationService).onPurchaseComplete(CONCERT_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 예상치 못한 예외 발생해도 onPurchaseComplete 호출됨")
    @Test
    void purchase_fail_unexpected_exception_but_complete_called() {
        // given
        given(queueOrchestrationService.isInProcessing(CONCERT_ID, USER_ID)).willReturn(true);
        willThrow(new RuntimeException("예상치 못한 오류"))
                .given(ticketStockService).decreaseByConcertId(CONCERT_ID, QUANTITY);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(CONCERT_ID, USER_ID, QUANTITY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("예상치 못한 오류");

        verify(queueOrchestrationService).onPurchaseComplete(CONCERT_ID, USER_ID);
    }
}
