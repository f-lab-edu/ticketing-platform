package com.ticket_service.seat.service;

import com.ticket_service.seat.dto.SeatSelectionResult;
import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.exception.SeatAlreadyHeldException;
import com.ticket_service.seat.exception.SeatAlreadyReservedException;
import com.ticket_service.seat.exception.SeatNotFoundException;
import com.ticket_service.seat.exception.SeatNotHeldByUserException;
import com.ticket_service.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatSelectionService {

    private final SeatRepository seatRepository;

    @Value("${seat.hold-timeout:5m}")
    private Duration holdTimeout;

    /**
     * 좌석 선택 (5분 잠금)
     * - DB 비관적 락으로 동시성 제어
     */
    @Transactional
    public SeatSelectionResult selectSeat(Long concertId, Long seatId, String userId) {
        Seat seat = seatRepository.findByIdAndConcertIdWithLock(seatId, concertId)
                .orElseThrow(() -> {
                    log.warn("좌석 조회 실패: concertId={}, seatId={}", concertId, seatId);
                    return new SeatNotFoundException();
                });

        if (seat.isReserved()) {
            log.warn("이미 예약된 좌석: concertId={}, seatId={}", concertId, seatId);
            throw new SeatAlreadyReservedException();
        }

        // HELD 상태이지만 만료되지 않은 경우 → 다른 사용자가 점유 중
        if (seat.isHeld() && !seat.isHoldExpired(holdTimeout)) {
            log.warn("이미 선택된 좌석: concertId={}, seatId={}, heldBy={}", concertId, seatId, seat.getHeldByUserId());
            throw new SeatAlreadyHeldException();
        }

        // 점유 (AVAILABLE이거나 만료된 HELD 상태)
        seat.hold(userId);

        log.info("좌석 선택 완료: concertId={}, seatId={}, userId={}, expiresIn={}s",
                concertId, seatId, userId, holdTimeout.toSeconds());

        return SeatSelectionResult.of(seat, holdTimeout.toSeconds());
    }

    /**
     * 좌석 선택 취소
     */
    @Transactional
    public void cancelSelection(Long concertId, Long seatId, String userId) {
        Seat seat = seatRepository.findByIdAndConcertIdWithLock(seatId, concertId)
                .orElseThrow(() -> {
                    log.warn("좌석 조회 실패: concertId={}, seatId={}", concertId, seatId);
                    return new SeatNotFoundException();
                });

        if (!seat.isHeldBy(userId)) {
            log.warn("좌석 선점 불일치: concertId={}, seatId={}, userId={}, heldBy={}",
                    concertId, seatId, userId, seat.getHeldByUserId());
            throw new SeatNotHeldByUserException();
        }

        seat.release();

        log.info("좌석 선택 취소: concertId={}, seatId={}, userId={}", concertId, seatId, userId);
    }
}
