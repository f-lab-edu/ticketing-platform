package com.ticket_service.ticket.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.ticket.controller.dto.DecreaseRequest;
import com.ticket_service.ticket.service.TicketStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TicketStockController {
    private final TicketStockService ticketStockService;

    @PostMapping("/ticket-stocks/{id}")
    public ApiResponse decrease(@PathVariable Long id, @RequestBody DecreaseRequest request) {
        ticketStockService.decrease(id, request.getRequestQuantity());

        return ApiResponse.success();
    }
}
