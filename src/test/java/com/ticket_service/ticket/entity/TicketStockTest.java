package com.ticket_service.ticket.entity;

import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStockTest {

    @Test
    @DisplayName("재고가 충분하면 요청 수량만큼 차감된다")
    void decreaseQuantity_success() {
        // given
        TicketStock ticketStock = TicketStock.builder()
                .totalQuantity(100)
                .remainingQuantity(100)
                .build();

        // when
        ticketStock.decreaseQuantity(10);

        // then
        assertThat(ticketStock.getRemainingQuantity()).isEqualTo(90);
    }

    @Test
    @DisplayName("차감 요청 수량이 0 이하이면 예외가 발생한다")
    void decreaseQuantity_quantityLessThanOrEqualZero() {
        // given
        TicketStock ticketStock = TicketStock.builder()
                .totalQuantity(100)
                .remainingQuantity(100)
                .build();

        // when & then
        assertThatThrownBy(() -> ticketStock.decreaseQuantity(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    @DisplayName("재고보다 많은 수량을 요청하면 재고 부족 예외가 발생한다")
    void decreaseQuantity_insufficientStock() {
        // given
        TicketStock ticketStock = TicketStock.builder()
                .totalQuantity(10)
                .remainingQuantity(5)
                .build();

        // when & then
        assertThatThrownBy(() -> ticketStock.decreaseQuantity(6))
                .isInstanceOf(InsufficientTicketStockException.class)
                .satisfies(ex -> {
                    InsufficientTicketStockException e =
                            (InsufficientTicketStockException) ex;

                    assertThat(e.getRemainingQuantity()).isEqualTo(5);
                    assertThat(e.getRequestQuantity()).isEqualTo(6);
                });
    }
}