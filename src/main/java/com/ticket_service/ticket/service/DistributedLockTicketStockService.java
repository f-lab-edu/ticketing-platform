package com.ticket_service.ticket.service;

import com.ticket_service.common.redis.LockKey;
import com.ticket_service.common.redis.RedissonLockTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "ticket.lock.strategy", havingValue = "distributed")
@RequiredArgsConstructor
public class DistributedLockTicketStockService implements TicketStockService {

    private final TicketStockTransactionalService ticketStockTransactionalService;
    private final RedissonLockTemplate redissonLockTemplate;

    @Override
    public void decreaseByConcertId(Long concertId, int requestQuantity) {
        redissonLockTemplate.executeWithLock(
                LockKey.ticketStock(concertId),
                () -> ticketStockTransactionalService.decreaseByConcertId(concertId, requestQuantity)
        );
    }
}
