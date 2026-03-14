package com.ticket_service.seat.dto;

import com.ticket_service.seat.entity.SeatGrade;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SectionResponse {
    private final SeatGrade grade;
    private final String displayName;
    private final int price;
    private final long totalSeats;
    private final long availableSeats;
}
