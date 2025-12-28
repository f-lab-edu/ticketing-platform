package com.ticket_service.ticket.entity;

import com.ticket_service.concert.entity.Concert;
import com.ticket_service.ticket.exception.InsufficientTicketStockException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class TicketStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    private Concert concert;

    private int totalQuantity;

    @Getter
    private int remainingQuantity;

    @Builder
    public TicketStock(Long id, Concert concert, int totalQuantity, int remainingQuantity) {
        validateQuantities(totalQuantity, remainingQuantity);

        this.id = id;
        this.concert = concert;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = remainingQuantity;
    }

    public void decreaseQuantity(int requestQuantity) {
        validateDecreaseQuantity(requestQuantity);

        remainingQuantity -= requestQuantity;
    }

    private void validateQuantities(int totalQuantity, int remainingQuantity) {
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("totalQuantity must be >= 0");
        }
        if (remainingQuantity < 0) {
            throw new IllegalArgumentException("remainingQuantity must be >= 0");
        }
        if (remainingQuantity > totalQuantity) {
            throw new IllegalArgumentException("remainingQuantity cannot exceed totalQuantity");
        }
    }

    private void validateDecreaseQuantity(int requestQuantity) {
        if (requestQuantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (remainingQuantity < requestQuantity) {
            throw new InsufficientTicketStockException(remainingQuantity, requestQuantity);
        }
    }
}
