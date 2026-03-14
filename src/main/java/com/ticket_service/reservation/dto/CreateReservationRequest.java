package com.ticket_service.reservation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateReservationRequest {
    private Long seatId;

    public CreateReservationRequest(Long seatId) {
        this.seatId = seatId;
    }
}
