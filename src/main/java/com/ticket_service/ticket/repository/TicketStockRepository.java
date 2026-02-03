package com.ticket_service.ticket.repository;

import com.ticket_service.ticket.entity.TicketStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketStockRepository extends JpaRepository<TicketStock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ts from TicketStock ts where ts.id = :id")
    Optional<TicketStock> findByIdWithPessimisticLock(@Param("id") Long id);

    Optional<TicketStock> findByConcertId(Long concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ts from TicketStock ts where ts.concert.id = :concertId")
    Optional<TicketStock> findByConcertIdWithPessimisticLock(@Param("concertId") Long concertId);
}
