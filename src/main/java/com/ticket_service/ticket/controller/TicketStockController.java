package com.ticket_service.ticket.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.ticket.controller.dto.DecreaseRequest;
import com.ticket_service.ticket.service.TicketPurchaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TicketStockController {

    private final TicketPurchaseService ticketPurchaseService;

    @PostMapping("/ticket-stocks/{id}")
    public ApiResponse<String> decrease(@PathVariable Long id, @RequestBody DecreaseRequest request) {
        ticketPurchaseService.purchase(id, request.getUserId(), request.getRequestQuantity());
        return ApiResponse.ok("success");
    }
}
