package com.ticket_service.ticket.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.ticket.controller.dto.DecreaseRequest;
import com.ticket_service.ticket.service.TicketStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 검증 없이 직접 티켓 재고를 차감하는 컨트롤러
 * 성능 테스트 및 개발 환경에서 락 전략별 성능 비교를 위해 사용
 */
@RestController
@RequiredArgsConstructor
public class TicketStockDirectController {

    private final TicketStockService ticketStockService;

    @PostMapping("/ticket-stocks/{id}/direct")
    public ApiResponse<String> decreaseDirect(@PathVariable Long id, @RequestBody DecreaseRequest request) {
        ticketStockService.decrease(id, request.getRequestQuantity());
        return ApiResponse.ok("success");
    }
}
