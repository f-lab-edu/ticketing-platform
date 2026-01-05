package com.ticket_service.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ApiResponse {

    private String message;

    public static ApiResponse success() {
        return new ApiResponse("SUCCESS");
    }
}