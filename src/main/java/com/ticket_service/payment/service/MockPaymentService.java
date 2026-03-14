package com.ticket_service.payment.service;

import com.ticket_service.payment.dto.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class MockPaymentService {

    /**
     * Mock 결제 처리 (항상 성공)
     * 실제 PG 연동 시 외부 API 호출로 500ms~2s 소요
     */
    public PaymentResult processPayment(String userId, int amount) {
        String transactionId = generateTransactionId();

        // 실제 PG 연동 시뮬레이션 (네트워크 지연)
        simulateNetworkDelay();

        log.info("결제 처리: userId={}, amount={}, transactionId={}", userId, amount, transactionId);

        return PaymentResult.success(transactionId, amount);
    }

    /**
     * Mock 환불 처리
     */
    public PaymentResult refund(String transactionId, int amount) {
        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        simulateNetworkDelay();

        log.info("환불 처리: transactionId={}, amount={}, refundId={}", transactionId, amount, refundId);

        return PaymentResult.success(refundId, amount);
    }

    private void simulateNetworkDelay() {
        try {
            // 실제 PG는 보통 500ms~2s 소요
            Thread.sleep(100);  // 테스트용으로 100ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
