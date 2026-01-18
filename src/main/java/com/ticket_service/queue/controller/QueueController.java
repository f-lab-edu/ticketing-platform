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

    /** 대기열 등록 및 현재 상태 반환 */
    @PostMapping("/{ticketStockId}/enter")
    public ApiResponse<QueueStatusResponse> enter(@PathVariable Long ticketStockId, @RequestBody QueueEnterRequest request) {
        QueueInfo queueInfo = queueService.registerAndGetInfo(ticketStockId, request.getUserId());
        return ApiResponse.ok(queueMapper.toResponse(queueInfo));
    }

    /** 대기열 상태 조회 (폴링용) */
    @GetMapping("/{ticketStockId}/status")
    public ApiResponse<QueueStatusResponse> getStatus(@PathVariable Long ticketStockId, @RequestParam String userId) {
        QueueInfo queueInfo = queueService.getQueueInfo(ticketStockId, userId);
        return ApiResponse.ok(queueMapper.toResponse(queueInfo));
    }

    /** 대기열 취소 (대기열/처리열 모두에서 제거) */
    @DeleteMapping("/{ticketStockId}")
    public ApiResponse<String> cancel(@PathVariable Long ticketStockId, @RequestParam String userId) {
        queueService.dequeue(ticketStockId, userId);
        return ApiResponse.ok("cancelled");
    }
}
