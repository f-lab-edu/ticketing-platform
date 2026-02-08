package com.ticket_service.ticket.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.ticket.controller.dto.PurchaseRequest;
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

    @PostMapping("/concerts/{concertId}/purchase")
    public ApiResponse<String> purchase(@PathVariable Long concertId, @RequestBody PurchaseRequest request) {
        ticketPurchaseService.purchase(concertId, request.getUserId(), request.getRequestQuantity());
        return ApiResponse.ok("success");
    }
}
