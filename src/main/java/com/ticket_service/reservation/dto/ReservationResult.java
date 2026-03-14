package com.ticket_service.reservation.dto;

import com.ticket_service.reservation.entity.Reservation;
import com.ticket_service.seat.entity.SeatGrade;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReservationResult {
    private final Long reservationId;
    private final String seatNumber;
    private final SeatGrade grade;
    private final int paidAmount;
    private final LocalDateTime confirmedAt;

    public static ReservationResult from(Reservation reservation) {
        return ReservationResult.builder()
                .reservationId(reservation.getId())
                .seatNumber(reservation.getSeat().getSeatNumber())
                .grade(reservation.getSeat().getGrade())
                .paidAmount(reservation.getPaidAmount())
                .confirmedAt(reservation.getReservedAt())
                .build();
    }
}
