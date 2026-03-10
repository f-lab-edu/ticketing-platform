package com.ticket_service.seat.entity;

import com.ticket_service.concert.entity.Concert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    private String seatNumber;

    @Enumerated(EnumType.STRING)
    private SeatGrade grade;

    private int price;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    private String heldByUserId;

    private LocalDateTime heldAt;

    @Builder
    public Seat(Concert concert, String seatNumber, SeatGrade grade, int price) {
        this.concert = concert;
        this.seatNumber = seatNumber;
        this.grade = grade;
        this.price = price;
        this.status = SeatStatus.AVAILABLE;
    }

    public void hold(String userId) {
        this.status = SeatStatus.HELD;
        this.heldByUserId = userId;
        this.heldAt = LocalDateTime.now();
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.heldByUserId = null;
        this.heldAt = null;
    }

    public boolean isHoldExpired(Duration holdTimeout) {
        if (this.status != SeatStatus.HELD || this.heldAt == null) {
            return true;
        }
        return this.heldAt.plus(holdTimeout).isBefore(LocalDateTime.now());
    }

    public boolean isHoldValid(String userId, Duration holdTimeout) {
        return this.status == SeatStatus.HELD
                && userId.equals(this.heldByUserId)
                && !isHoldExpired(holdTimeout);
    }

    public void reserve() {
        this.status = SeatStatus.RESERVED;
    }

    public boolean isAvailable() {
        return this.status == SeatStatus.AVAILABLE;
    }

    public boolean isHeld() {
        return this.status == SeatStatus.HELD;
    }

    public boolean isHeldBy(String userId) {
        return this.status == SeatStatus.HELD && userId.equals(this.heldByUserId);
    }

    public boolean isReserved() {
        return this.status == SeatStatus.RESERVED;
    }
}
