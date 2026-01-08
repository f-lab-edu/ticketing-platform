package com.ticket_service.ticket.service;

import com.ticket_service.ticket.entity.TicketStock;
import com.ticket_service.ticket.repository.TicketStockRepository;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "ticket.lock.strategy", havingValue = "optimistic")
@RequiredArgsConstructor
public class OptimisticLockTicketStockService implements TicketStockService {

    private final TicketStockRepository ticketStockRepository;

    @Override
    @Retryable(
            retryFor = {OptimisticLockException.class, ObjectOptimisticLockingFailureException.class},
            maxAttempts = 50,
            backoff = @Backoff(delay = 50)
    )
    @Transactional
    public void decrease(Long ticketStockId, int requestQuantity) {
        TicketStock ticketStock = ticketStockRepository.findById(ticketStockId)
                .orElseThrow(() -> new IllegalArgumentException("TicketStock not found"));

        ticketStock.decreaseQuantity(requestQuantity);
    }

    @Recover
    public void recover(OptimisticLockException e, Long ticketStockId, int requestQuantity) {
        throw new IllegalStateException("최대 재시도 횟수를 초과했습니다. ticketStockId: " + ticketStockId);
    }

    @Recover
    public void recover(ObjectOptimisticLockingFailureException e, Long ticketStockId, int requestQuantity) {
        throw new IllegalStateException("최대 재시도 횟수를 초과했습니다. ticketStockId: " + ticketStockId);
    }
}
