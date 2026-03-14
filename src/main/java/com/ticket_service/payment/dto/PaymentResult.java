package com.ticket_service.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResult {
    private final String transactionId;
    private final PaymentStatus status;
    private final int amount;
    private final LocalDateTime processedAt;

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public static PaymentResult success(String transactionId, int amount) {
        return PaymentResult.builder()
                .transactionId(transactionId)
                .status(PaymentStatus.SUCCESS)
                .amount(amount)
                .processedAt(LocalDateTime.now())
                .build();
    }

    public static PaymentResult failed(String transactionId, int amount) {
        return PaymentResult.builder()
                .transactionId(transactionId)
                .status(PaymentStatus.FAILED)
                .amount(amount)
                .processedAt(LocalDateTime.now())
                .build();
    }
}
