package com.ticket_service.seat.service;

import com.ticket_service.seat.dto.SeatListResponse;
import com.ticket_service.seat.dto.SeatResponse;
import com.ticket_service.seat.dto.SectionListResponse;
import com.ticket_service.seat.dto.SectionResponse;
import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.entity.SeatGrade;
import com.ticket_service.seat.entity.SeatStatus;
import com.ticket_service.seat.exception.SeatNotFoundException;
import com.ticket_service.seat.mapper.SeatMapper;
import com.ticket_service.seat.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeatQueryService {

    private final SeatRepository seatRepository;
    private final SeatMapper seatMapper;

    /**
     * 콘서트의 전체 좌석 목록 조회
     */
    public SeatListResponse getSeats(Long concertId) {
        List<Seat> seats = seatRepository.findByConcertId(concertId);

        log.debug("좌석 목록 조회: concertId={}, total={}, available={}",
                concertId, seats.size(), seats.stream().filter(Seat::isAvailable).count());

        return seatMapper.toListResponse(seats);
    }

    /**
     * 특정 상태의 좌석 목록 조회
     */
    public List<SeatResponse> getSeatsByStatus(Long concertId, SeatStatus status) {
        List<Seat> seats = seatRepository.findByConcertIdAndStatus(concertId, status);
        return seatMapper.toResponseList(seats);
    }

    /**
     * 좌석 단건 조회
     */
    public Seat getSeat(Long concertId, Long seatId) {
        return seatRepository.findByIdAndConcertId(seatId, concertId)
                .orElseThrow(() -> {
                    log.warn("좌석 조회 실패: concertId={}, seatId={}", concertId, seatId);
                    return new SeatNotFoundException();
                });
    }

    /**
     * 좌석 수 조회
     */
    public long countSeats(Long concertId) {
        return seatRepository.countByConcertId(concertId);
    }

    /**
     * 가용 좌석 수 조회
     */
    public long countAvailableSeats(Long concertId) {
        return seatRepository.countByConcertIdAndStatus(concertId, SeatStatus.AVAILABLE);
    }

    /**
     * 구역(등급) 목록 조회
     */
    public SectionListResponse getSections(Long concertId) {
        List<SectionResponse> sections = Arrays.stream(SeatGrade.values())
                .map(grade -> {
                    long total = seatRepository.countByConcertIdAndGrade(concertId, grade);
                    long available = seatRepository.countByConcertIdAndGradeAndStatus(concertId, grade, SeatStatus.AVAILABLE);
                    return SectionResponse.builder()
                            .grade(grade)
                            .displayName(grade.getDisplayName())
                            .price(grade.getDefaultPrice())
                            .totalSeats(total)
                            .availableSeats(available)
                            .build();
                })
                .filter(section -> section.getTotalSeats() > 0)
                .toList();

        long totalSeats = sections.stream().mapToLong(SectionResponse::getTotalSeats).sum();
        long availableSeats = sections.stream().mapToLong(SectionResponse::getAvailableSeats).sum();

        log.debug("구역 목록 조회: concertId={}, sections={}", concertId, sections.size());

        return SectionListResponse.builder()
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .sections(sections)
                .build();
    }

    /**
     * 등급별 좌석 목록 조회
     */
    public SeatListResponse getSeatsByGrade(Long concertId, SeatGrade grade) {
        List<Seat> seats = seatRepository.findByConcertIdAndGrade(concertId, grade);

        log.debug("등급별 좌석 조회: concertId={}, grade={}, total={}, available={}",
                concertId, grade, seats.size(), seats.stream().filter(Seat::isAvailable).count());

        return seatMapper.toListResponse(seats);
    }
}
