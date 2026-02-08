package com.ticket_service.queue.controller;

import com.ticket_service.common.dto.ApiResponse;
import com.ticket_service.queue.controller.dto.QueueEnterRequest;
import com.ticket_service.queue.service.QueueOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueOrchestrationService queueOrchestrationService;

    /** 대기열 등록 + SSE 구독 */
    @PostMapping(value = "/{concertId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable Long concertId, @RequestBody QueueEnterRequest request) {
        return queueOrchestrationService.registerAndSubscribe(concertId, request.getUserId());
    }

    /** 대기열 취소 */
    @DeleteMapping("/{concertId}")
    public ApiResponse<String> cancel(@PathVariable Long concertId, @RequestParam String userId) {
        queueOrchestrationService.onCancel(concertId, userId);
        return ApiResponse.ok("cancelled");
    }
}
