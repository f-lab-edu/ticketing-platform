package com.ticket_service.reservation.service;

import com.ticket_service.payment.dto.PaymentResult;
import com.ticket_service.payment.service.MockPaymentService;
import com.ticket_service.reservation.dto.ReservationResult;
import com.ticket_service.reservation.entity.Reservation;
import com.ticket_service.reservation.exception.ReservationException;
import com.ticket_service.seat.entity.Seat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 예약 서비스 (Facade)
 *
 * 실무에서 PG 연동 시 결제는 외부 API 호출이므로
 * DB 트랜잭션 밖에서 처리해야 함.
 *
 * 흐름:
 * 1. [트랜잭션1] 좌석 상태 HELD → PAYING (짧은 락)
 * 2. [트랜잭션X] 결제 처리 (외부 API, 500ms~2s)
 * 3. [트랜잭션2] 결제 성공 시 → RESERVED + 예약 생성
 *    [트랜잭션2] 결제 실패 시 → PAYING → HELD 롤백
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationTransactionService transactionService;
    private final MockPaymentService paymentService;

    /**
     * 예약 생성 (트랜잭션 분리)
     */
    public ReservationResult createReservation(Long concertId, Long seatId, String userId) {
        // 1단계: 결제 시작 (짧은 트랜잭션)
        Seat seat = transactionService.startPayment(concertId, seatId, userId);

        // 2단계: 결제 처리 (트랜잭션 밖 - 외부 API 호출)
        PaymentResult paymentResult;
        try {
            paymentResult = paymentService.processPayment(userId, seat.getPrice());
        } catch (Exception e) {
            // 결제 중 예외 발생 시 좌석 상태 롤백
            log.error("결제 처리 중 예외 발생: concertId={}, seatId={}, userId={}", concertId, seatId, userId, e);
            transactionService.rollbackPayment(seatId);
            throw ReservationException.paymentFailed();
        }

        // 결제 실패 시 롤백
        if (!paymentResult.isSuccess()) {
            transactionService.rollbackPayment(seatId);
            throw ReservationException.paymentFailed();
        }

        // 3단계: 예약 확정 (짧은 트랜잭션)
        try {
            Reservation reservation = transactionService.confirmReservation(concertId, seatId, userId, paymentResult);
            return ReservationResult.from(reservation);
        } catch (Exception e) {
            // 예약 확정 실패 시 환불 처리
            log.error("예약 확정 실패, 환불 진행: transactionId={}", paymentResult.getTransactionId(), e);
            paymentService.refund(paymentResult.getTransactionId(), paymentResult.getAmount());
            throw e;
        }
    }
}
