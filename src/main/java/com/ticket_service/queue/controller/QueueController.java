package com.ticket_service.queue.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.queue.controller.dto.QueueEnterRequest;
import com.ticket_service.queue.controller.dto.QueueStatusResponse;
import com.ticket_service.queue.controller.mapper.QueueMapper;
import com.ticket_service.queue.domain.QueueInfo;
import com.ticket_service.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final QueueMapper queueMapper;

    @PostMapping("/{ticketStockId}/enter")
    public ApiResponse<QueueStatusResponse> enter(@PathVariable Long ticketStockId, @RequestBody QueueEnterRequest request) {
        QueueInfo queueInfo = queueService.registerAndGetInfo(ticketStockId, request.getUserId());
        return ApiResponse.ok(queueMapper.toResponse(queueInfo));
    }

    @GetMapping("/{ticketStockId}/status")
    public ApiResponse<QueueStatusResponse> getStatus(@PathVariable Long ticketStockId, @RequestParam String userId) {
        QueueInfo queueInfo = queueService.getQueueInfo(ticketStockId, userId);
        return ApiResponse.ok(queueMapper.toResponse(queueInfo));
    }

    @DeleteMapping("/{ticketStockId}")
    public ApiResponse<String> cancel(@PathVariable Long ticketStockId, @RequestParam String userId) {
        queueService.dequeue(ticketStockId, userId);
        return ApiResponse.ok("cancelled");
    }
}
