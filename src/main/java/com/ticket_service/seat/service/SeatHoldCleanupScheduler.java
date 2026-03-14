package com.ticket_service.seat.service;

import com.ticket_service.queue.service.QueueOrchestrationService;
import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.entity.SeatStatus;
import com.ticket_service.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 만료된 좌석 점유를 정리하는 스케줄러
 * - HELD 상태이면서 holdTimeout이 지난 좌석을 찾아 AVAILABLE로 변경
 * - 처리 카운터 감소 + 다음 대기자 입장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldCleanupScheduler {

    private final SeatRepository seatRepository;
    private final QueueOrchestrationService queueOrchestrationService;

    @Value("${seat.hold-timeout:5m}")
    private Duration holdTimeout;

    @Scheduled(fixedRateString = "${seat.hold-check-interval:30000}")
    @Transactional
    public void cleanupExpiredHolds() {
        LocalDateTime expiredBefore = LocalDateTime.now().minus(holdTimeout);
        List<Seat> expiredSeats = seatRepository.findExpiredHolds(SeatStatus.HELD, expiredBefore);

        if (expiredSeats.isEmpty()) {
            return;
        }

        log.debug("만료된 좌석 점유 정리 시작: count={}", expiredSeats.size());

        for (Seat seat : expiredSeats) {
            Long concertId = seat.getConcert().getId();
            Long seatId = seat.getId();

            // 비관적 락으로 재조회하여 상태 확인
            Seat lockedSeat = seatRepository.findByIdAndConcertIdWithLock(seatId, concertId)
                    .orElse(null);

            if (lockedSeat == null) {
                continue;
            }

            // 락 획득 후 상태 재확인 (다른 트랜잭션에 의해 변경됐을 수 있음)
            if (!lockedSeat.isHeld() || !lockedSeat.isHoldExpired(holdTimeout)) {
                continue;
            }

            lockedSeat.release();

            queueOrchestrationService.onSeatHoldExpired(concertId);

            log.info("좌석 점유 만료 처리: concertId={}, seatId={}, seatNumber={}",
                    concertId, seatId, lockedSeat.getSeatNumber());
        }
    }
}
