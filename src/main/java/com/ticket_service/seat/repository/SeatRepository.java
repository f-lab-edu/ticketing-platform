package com.ticket_service.seat.repository;

import com.ticket_service.seat.entity.Seat;
import com.ticket_service.seat.entity.SeatGrade;
import com.ticket_service.seat.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByConcertId(Long concertId);

    List<Seat> findByConcertIdAndStatus(Long concertId, SeatStatus status);

    List<Seat> findByStatus(SeatStatus status);

    Optional<Seat> findByIdAndConcertId(Long seatId, Long concertId);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.concert.id = :concertId AND s.status = :status")
    long countByConcertIdAndStatus(@Param("concertId") Long concertId, @Param("status") SeatStatus status);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.concert.id = :concertId")
    long countByConcertId(@Param("concertId") Long concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :seatId AND s.concert.id = :concertId")
    Optional<Seat> findByIdAndConcertIdWithLock(@Param("seatId") Long seatId, @Param("concertId") Long concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :seatId")
    Optional<Seat> findByIdWithLock(@Param("seatId") Long seatId);

    @Query("SELECT s FROM Seat s WHERE s.status = :status AND s.heldAt < :expiredBefore")
    List<Seat> findExpiredHolds(@Param("status") SeatStatus status, @Param("expiredBefore") LocalDateTime expiredBefore);

    List<Seat> findByConcertIdAndGrade(Long concertId, SeatGrade grade);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.concert.id = :concertId AND s.grade = :grade")
    long countByConcertIdAndGrade(@Param("concertId") Long concertId, @Param("grade") SeatGrade grade);

    @Query("SELECT COUNT(s) FROM Seat s WHERE s.concert.id = :concertId AND s.grade = :grade AND s.status = :status")
    long countByConcertIdAndGradeAndStatus(@Param("concertId") Long concertId, @Param("grade") SeatGrade grade, @Param("status") SeatStatus status);
}
