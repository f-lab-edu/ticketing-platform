package com.ticket_service.seat.dto;

import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.entity.SeatGrade;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatSelectionResult {
    private final Long seatId;
    private final String seatNumber;
    private final SeatGrade grade;
    private final int price;
    private final long holdExpiresInSeconds;

    public static SeatSelectionResult of(Seat seat, long holdExpiresInSeconds) {
        return SeatSelectionResult.builder()
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .grade(seat.getGrade())
                .price(seat.getPrice())
                .holdExpiresInSeconds(holdExpiresInSeconds)
                .build();
    }
}
