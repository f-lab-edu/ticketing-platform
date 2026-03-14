package com.ticket_service.seat.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SeatGrade {
    VIP("VIP", 150000),
    R("R", 120000),
    S("S", 90000),
    A("A", 60000);

    private final String displayName;
    private final int defaultPrice;
}
