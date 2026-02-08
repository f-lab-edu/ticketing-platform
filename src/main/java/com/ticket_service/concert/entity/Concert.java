package com.ticket_service.concert.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Concert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private LocalDateTime openAt;

    private LocalDateTime closeAt;

    @Builder
    public Concert(String title, LocalDateTime openAt, LocalDateTime closeAt) {
        this.title = title;
        this.openAt = openAt;
        this.closeAt = closeAt;
    }
}
