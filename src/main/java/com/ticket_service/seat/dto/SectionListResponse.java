package com.ticket_service.seat.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SectionListResponse {
    private final long totalSeats;
    private final long availableSeats;
    private final List<SectionResponse> sections;
}