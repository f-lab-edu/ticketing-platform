package com.ticket_service.ticket.service;

import com.ticket_service.queue.exception.QueueAccessDeniedException;
import com.ticket_service.queue.service.QueueService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketPurchaseServiceTest {

    @Mock
    private QueueService queueService;

    @Mock
    private TicketStockService ticketStockService;

    @InjectMocks
    private TicketPurchaseService ticketPurchaseService;

    private static final Long TICKET_STOCK_ID = 1L;
    private static final String USER_ID = "user-1";
    private static final int QUANTITY = 1;

    @DisplayName("구매 성공 - 검증 → 입장 → 차감 → 완료 순서로 실행")
    @Test
    void purchase_success() {
        // given
        given(queueService.canEnter(TICKET_STOCK_ID, USER_ID)).willReturn(true);

        // when
        ticketPurchaseService.purchase(TICKET_STOCK_ID, USER_ID, QUANTITY);

        // then
        InOrder inOrder = inOrder(queueService, ticketStockService);
        inOrder.verify(queueService).canEnter(TICKET_STOCK_ID, USER_ID);
        inOrder.verify(queueService).enter(TICKET_STOCK_ID, USER_ID);
        inOrder.verify(ticketStockService).decrease(TICKET_STOCK_ID, QUANTITY);
        inOrder.verify(queueService).complete(TICKET_STOCK_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 대기열 순서가 아닐 때 예외 발생")
    @Test
    void purchase_fail_queue_access_denied() {
        // given
        given(queueService.canEnter(TICKET_STOCK_ID, USER_ID)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(TICKET_STOCK_ID, USER_ID, QUANTITY))
                .isInstanceOf(QueueAccessDeniedException.class)
                .hasMessage("대기열 순서가 아닙니다. 대기열 상태를 확인해주세요.");

        verify(queueService).canEnter(TICKET_STOCK_ID, USER_ID);
        verify(queueService, never()).enter(TICKET_STOCK_ID, USER_ID);
        verify(ticketStockService, never()).decrease(TICKET_STOCK_ID, QUANTITY);
        verify(queueService, never()).complete(TICKET_STOCK_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 재고 부족해도 complete 호출됨 (finally 블록)")
    @Test
    void purchase_fail_insufficient_stock_but_complete_called() {
        // given
        given(queueService.canEnter(TICKET_STOCK_ID, USER_ID)).willReturn(true);
        willThrow(new InsufficientTicketStockException(0, QUANTITY))
                .given(ticketStockService).decrease(TICKET_STOCK_ID, QUANTITY);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(TICKET_STOCK_ID, USER_ID, QUANTITY))
                .isInstanceOf(InsufficientTicketStockException.class);

        InOrder inOrder = inOrder(queueService, ticketStockService);
        inOrder.verify(queueService).canEnter(TICKET_STOCK_ID, USER_ID);
        inOrder.verify(queueService).enter(TICKET_STOCK_ID, USER_ID);
        inOrder.verify(ticketStockService).decrease(TICKET_STOCK_ID, QUANTITY);
        inOrder.verify(queueService).complete(TICKET_STOCK_ID, USER_ID);
    }

    @DisplayName("구매 실패 - 예상치 못한 예외 발생해도 complete 호출됨")
    @Test
    void purchase_fail_unexpected_exception_but_complete_called() {
        // given
        given(queueService.canEnter(TICKET_STOCK_ID, USER_ID)).willReturn(true);
        willThrow(new RuntimeException("예상치 못한 오류"))
                .given(ticketStockService).decrease(TICKET_STOCK_ID, QUANTITY);

        // when & then
        assertThatThrownBy(() -> ticketPurchaseService.purchase(TICKET_STOCK_ID, USER_ID, QUANTITY))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("예상치 못한 오류");

        verify(queueService).complete(TICKET_STOCK_ID, USER_ID);
    }
}
