package com.ticket_service.reservation.controller;

import com.ticket_service.auth.annotation.QueueToken;
import com.ticket_service.auth.dto.QueueTokenClaims;
import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.reservation.dto.CreateReservationRequest;
import com.ticket_service.reservation.dto.ReservationResult;
import com.ticket_service.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concerts/{concertId}/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * 예약 확정
     */
    @PostMapping
    public ApiResponse<ReservationResult> createReservation(
            @PathVariable Long concertId,
            @RequestBody CreateReservationRequest request,
            @QueueToken QueueTokenClaims claims) {

        ReservationResult result = reservationService.createReservation(concertId, request.getSeatId(), claims.getUserId());
        return ApiResponse.ok(result);
    }
}
