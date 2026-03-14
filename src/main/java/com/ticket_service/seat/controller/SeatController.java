package com.ticket_service.seat.controller;

import com.ticket_service.auth.annotation.QueueToken;
import com.ticket_service.auth.dto.QueueTokenClaims;
import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.seat.dto.SeatListResponse;
import com.ticket_service.seat.dto.SeatSelectionResult;
import com.ticket_service.seat.dto.SectionListResponse;
import com.ticket_service.seat.entity.SeatGrade;
import com.ticket_service.seat.service.SeatQueryService;
import com.ticket_service.seat.service.SeatSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/concerts/{concertId}")
@RequiredArgsConstructor
public class SeatController {

    private final SeatQueryService seatQueryService;
    private final SeatSelectionService seatSelectionService;

    /**
     * 구역(등급) 목록 조회
     */
    @GetMapping("/sections")
    public ApiResponse<SectionListResponse> getSections(
            @PathVariable Long concertId,
            @QueueToken QueueTokenClaims claims) {

        SectionListResponse response = seatQueryService.getSections(concertId);
        return ApiResponse.ok(response);
    }

    /**
     * 등급별 좌석 목록 조회
     */
    @GetMapping("/sections/{grade}/seats")
    public ApiResponse<SeatListResponse> getSeatsByGrade(
            @PathVariable Long concertId,
            @PathVariable SeatGrade grade,
            @QueueToken QueueTokenClaims claims) {

        SeatListResponse response = seatQueryService.getSeatsByGrade(concertId, grade);
        return ApiResponse.ok(response);
    }

    /**
     * 좌석 선택 (5분 잠금)
     */
    @PostMapping("/seats/{seatId}/select")
    public ApiResponse<SeatSelectionResult> selectSeat(
            @PathVariable Long concertId,
            @PathVariable Long seatId,
            @QueueToken QueueTokenClaims claims) {

        SeatSelectionResult result = seatSelectionService.selectSeat(concertId, seatId, claims.getUserId());
        return ApiResponse.ok(result);
    }

    /**
     * 좌석 선택 취소
     */
    @DeleteMapping("/seats/{seatId}/select")
    public ApiResponse<String> cancelSelection(
            @PathVariable Long concertId,
            @PathVariable Long seatId,
            @QueueToken QueueTokenClaims claims) {

        seatSelectionService.cancelSelection(concertId, seatId, claims.getUserId());
        return ApiResponse.ok("좌석 선택이 취소되었습니다.");
    }
}
