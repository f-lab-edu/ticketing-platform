package com.ticket_service.seat.mapper;

import com.ticket_service.seat.dto.SeatListResponse;
import com.ticket_service.seat.dto.SeatResponse;
import com.ticket_service.seat.entity.Seat;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeatMapper {

    public SeatResponse toResponse(Seat seat) {
        return SeatResponse.builder()
                .seatId(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .grade(seat.getGrade())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .build();
    }

    public List<SeatResponse> toResponseList(List<Seat> seats) {
        return seats.stream()
                .map(this::toResponse)
                .toList();
    }

    public SeatListResponse toListResponse(List<Seat> seats) {
        long totalSeats = seats.size();
        long availableSeats = seats.stream()
                .filter(Seat::isAvailable)
                .count();

        List<SeatResponse> seatResponses = toResponseList(seats);

        return SeatListResponse.builder()
                .totalSeats(totalSeats)
                .availableSeats(availableSeats)
                .seats(seatResponses)
                .build();
    }
}
