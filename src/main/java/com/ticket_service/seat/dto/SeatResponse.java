package com.ticket_service.seat.dto;

import com.ticket_service.seat.entity.SeatGrade;
import com.ticket_service.seat.entity.SeatStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SeatResponse {
    private final Long seatId;
    private final String seatNumber;
    private final SeatGrade grade;
    private final int price;
    private final SeatStatus status;
}
