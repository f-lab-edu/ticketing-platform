package com.ticket_service.reservation.service;

import com.ticket_service.concert.entity.Concert;
import com.ticket_service.concert.repository.ConcertRepository;
import com.ticket_service.payment.dto.PaymentResult;
import com.ticket_service.queue.service.QueueOrchestrationService;
import com.ticket_service.reservation.entity.Reservation;
import com.ticket_service.reservation.exception.ReservationException;
import com.ticket_service.reservation.repository.ReservationRepository;
import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.exception.SeatNotFoundException;
import com.ticket_service.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 예약 관련 트랜잭션 처리 담당
 *
 * Spring AOP의 self-invocation 문제를 피하기 위해
 * 트랜잭션이 필요한 메서드를 별도 클래스로 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationTransactionService {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final ConcertRepository concertRepository;
    private final QueueOrchestrationService queueOrchestrationService;

    @Value("${seat.hold-timeout:5m}")
    private Duration holdTimeout;

    /**
     * 1단계: 결제 시작 - 좌석 상태를 PAYING으로 변경
     * 짧은 트랜잭션으로 락 시간 최소화
     */
    @Transactional
    public Seat startPayment(Long concertId, Long seatId, String userId) {
        Seat seat = seatRepository.findByIdAndConcertIdWithLock(seatId, concertId)
                .orElseThrow(() -> {
                    log.warn("좌석 조회 실패: concertId={}, seatId={}", concertId, seatId);
                    return new SeatNotFoundException();
                });

        // 좌석 점유 유효성 확인
        if (!seat.isHoldValid(userId, holdTimeout)) {
            log.warn("좌석 점유 무효: concertId={}, seatId={}, userId={}, status={}, heldBy={}",
                    concertId, seatId, userId, seat.getStatus(), seat.getHeldByUserId());
            throw ReservationException.seatHoldExpired();
        }

        // 상태 변경: HELD → PAYING
        seat.startPayment();
        log.debug("결제 시작: concertId={}, seatId={}, userId={}", concertId, seatId, userId);

        return seat;
    }

    /**
     * 결제 실패 시 롤백 - PAYING → HELD
     */
    @Transactional
    public void rollbackPayment(Long seatId) {
        seatRepository.findById(seatId).ifPresent(seat -> {
            if (seat.isPaying()) {
                seat.cancelPayment();
                log.info("결제 롤백: seatId={}", seatId);
            }
        });
    }

    /**
     * 3단계: 예약 확정 - PAYING → RESERVED + 예약 생성
     */
    @Transactional
    public Reservation confirmReservation(Long concertId, Long seatId, String userId, PaymentResult paymentResult) {
        // 비관적 락으로 좌석 재조회
        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(SeatNotFoundException::new);

        // 결제 중 상태인지 확인
        if (!seat.isPayingBy(userId)) {
            log.error("좌석 상태 불일치: seatId={}, expected=PAYING by {}, actual={} by {}",
                    seatId, userId, seat.getStatus(), seat.getHeldByUserId());
            throw ReservationException.seatStatusMismatch();
        }

        // 콘서트 조회
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(ReservationException::concertNotFound);

        // 좌석 상태 변경: PAYING → RESERVED
        seat.reserve();

        // 예약 생성
        Reservation reservation = Reservation.builder()
                .concert(concert)
                .seat(seat)
                .userId(userId)
                .paidAmount(paymentResult.getAmount())
                .build();
        reservationRepository.save(reservation);

        // 처리 카운터 감소 + 다음 대기자 입장
        queueOrchestrationService.onReservationComplete(concertId, userId);

        log.info("예약 완료: reservationId={}, concertId={}, seatId={}, userId={}, transactionId={}",
                reservation.getId(), concertId, seatId, userId, paymentResult.getTransactionId());

        return reservation;
    }
}
