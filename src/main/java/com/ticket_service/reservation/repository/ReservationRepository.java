package com.ticket_service.reservation.repository;

import com.ticket_service.reservation.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByConcertId(Long concertId);

    List<Reservation> findByUserId(String userId);

    Optional<Reservation> findBySeatId(Long seatId);

    boolean existsBySeatId(Long seatId);
}
