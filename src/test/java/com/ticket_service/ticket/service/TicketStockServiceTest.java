package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import com.ticket_service.ticket.repository.TicketStockRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketStockServiceTest {
    @Mock
    private TicketStockRepository ticketStockRepository;

    @InjectMocks
    private TicketStockService ticketStockService;

    @DisplayName("재고 차감 성공 - 재고가 충분한 경우")
    @Test
    void decrease_success_when_sufficient_stock() {
        // given
        Long ticketStockId = 1L;
        int requestQuantity = 5;
        int initialQuantity = 100;
        int resultRemainingQuantity = 95;

        TicketStock ticketStock = TicketStock.builder()
                .totalQuantity(initialQuantity)
                .remainingQuantity(initialQuantity)
                .build();

        given(ticketStockRepository.findByIdWithPessimisticLock(ticketStockId))
                .willReturn(Optional.of(ticketStock));

        // when
        ticketStockService.decrease(ticketStockId, requestQuantity);

        // then
        assertThat(ticketStock.getRemainingQuantity()).isEqualTo(resultRemainingQuantity);
        verify(ticketStockRepository).findByIdWithPessimisticLock(ticketStockId);
    }

    @DisplayName("재고 차감 실패 - 재고가 부족한 경우 예외 발생")
    @Test
    void decrease_fail_when_insufficient_stock() {
        // given
        Long ticketStockId = 1L;
        int requestQuantity = 1;
        int remainingQuantity = 0;
        int resultRemainingQuantity = 0;

        TicketStock ticketStock = TicketStock.builder()
                .totalQuantity(100)
                .remainingQuantity(remainingQuantity)
                .build();

        given(ticketStockRepository.findByIdWithPessimisticLock(ticketStockId))
                .willReturn(Optional.of(ticketStock));

        // when & then
        assertThatThrownBy(() -> ticketStockService.decrease(ticketStockId, requestQuantity))
                .isInstanceOf(InsufficientTicketStockException.class);

        // 재고는 변경되지 않아야 함
        assertThat(ticketStock.getRemainingQuantity()).isEqualTo(resultRemainingQuantity);
        verify(ticketStockRepository).findByIdWithPessimisticLock(ticketStockId);
    }

    @DisplayName("재고 차감 실패 - 존재하지 않는 티켓 재고인 경우 예외 발생")
    @Test
    void decrease_fail_when_ticket_stock_not_found() {
        // given
        Long nonExistentTicketStockId = 999L;
        int requestQuantity = 1;

        given(ticketStockRepository.findByIdWithPessimisticLock(nonExistentTicketStockId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> ticketStockService.decrease(nonExistentTicketStockId, requestQuantity))
                .isInstanceOf(IllegalArgumentException.class);

        verify(ticketStockRepository).findByIdWithPessimisticLock(nonExistentTicketStockId);
    }
}
