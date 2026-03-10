package com.ticket_service.seat.service;

import com.ticket_service.concert.entity.Concert;
import com.ticket_service.concert.repository.ConcertRepository;
import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.entity.SeatGrade;
import com.ticket_service.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 좌석 초기 데이터 생성기
 * 콘서트당 약 15,000석 규모 (실제 대형 콘서트장 기준)
 *
 * 좌석 구성:
 * - VIP: 500석 (섹션 A, 5열 × 100석)
 * - R석: 2,000석 (섹션 B, 20열 × 100석)
 * - S석: 4,500석 (섹션 C, 30열 × 150석)
 * - A석: 8,000석 (섹션 D, 40열 × 200석)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!test")
public class SeatDataInitializer implements CommandLineRunner {

    private final ConcertRepository concertRepository;
    private final SeatRepository seatRepository;

    private static final int BATCH_SIZE = 1000;

    @Override
    @Transactional
    public void run(String... args) {
        List<Concert> concerts = concertRepository.findAll();

        if (concerts.isEmpty()) {
            Concert concert = createDefaultConcert();
            concerts = List.of(concert);
        }

        for (Concert concert : concerts) {
            if (seatRepository.countByConcertId(concert.getId()) > 0) {
                log.info("좌석이 이미 존재합니다: concertId={}", concert.getId());
                continue;
            }

            int totalSeats = generateSeatsInBatches(concert);
            log.info("좌석 초기화 완료: concertId={}, seatCount={}", concert.getId(), totalSeats);
        }
    }

    private Concert createDefaultConcert() {
        LocalDateTime now = LocalDateTime.now();
        Concert concert = Concert.builder()
                .title("2026 봄 콘서트")
                .openAt(now.plusDays(7))
                .closeAt(now.plusDays(7).plusHours(3))
                .build();
        concertRepository.save(concert);
        log.info("콘서트 초기화 완료: id={}, title={}", concert.getId(), concert.getTitle());
        return concert;
    }

    private int generateSeatsInBatches(Concert concert) {
        List<Seat> batch = new ArrayList<>(BATCH_SIZE);
        int totalCount = 0;

        // VIP 좌석: 섹션 A, 5열 × 100석 = 500석
        totalCount += generateSection(concert, batch, "A", SeatGrade.VIP, 5, 100);

        // R 좌석: 섹션 B, 20열 × 100석 = 2,000석
        totalCount += generateSection(concert, batch, "B", SeatGrade.R, 20, 100);

        // S 좌석: 섹션 C, 30열 × 150석 = 4,500석
        totalCount += generateSection(concert, batch, "C", SeatGrade.S, 30, 150);

        // A 좌석: 섹션 D, 40열 × 200석 = 8,000석
        totalCount += generateSection(concert, batch, "D", SeatGrade.A, 40, 200);

        // 남은 배치 저장
        if (!batch.isEmpty()) {
            seatRepository.saveAll(batch);
        }

        return totalCount;
    }

    private int generateSection(Concert concert, List<Seat> batch, String section,
                                 SeatGrade grade, int rows, int seatsPerRow) {
        int count = 0;

        for (int row = 1; row <= rows; row++) {
            for (int seatNum = 1; seatNum <= seatsPerRow; seatNum++) {
                String seatNumber = String.format("%s%d-%d", section, row, seatNum);

                batch.add(Seat.builder()
                        .concert(concert)
                        .seatNumber(seatNumber)
                        .grade(grade)
                        .price(grade.getDefaultPrice())
                        .build());

                count++;

                if (batch.size() >= BATCH_SIZE) {
                    seatRepository.saveAll(batch);
                    batch.clear();
                }
            }
        }

        return count;
    }
}
