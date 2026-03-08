package com.ticket_service.queue.service;

import com.ticket_service.common.redis.QueueKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProcessingCounter {

    private final RedisTemplate<String, String> redisTemplate;

    public ProcessingCounter(@Qualifier("queueRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Value("${processing.max-count:200}")
    private int maxProcessingCount;

    /**
     * 카운터 증가 (입장 시)
     * @return true: 성공, false: 최대값 초과
     */
    public boolean tryIncrement(Long concertId) {
        String key = QueueKey.processingCounter(concertId);
        Long current = redisTemplate.opsForValue().increment(key);

        if (current == null) {
            log.error("카운터 증가 실패: concertId={}", concertId);
            return false;
        }

        if (current > maxProcessingCount) {
            redisTemplate.opsForValue().decrement(key);
            log.debug("최대 처리 용량 초과: concertId={}, current={}, max={}",
                    concertId, current, maxProcessingCount);
            return false;
        }

        log.debug("카운터 증가: concertId={}, current={}", concertId, current);
        return true;
    }

    /**
     * 카운터 감소 (예약 완료/잠금 만료 시)
     */
    public void decrement(Long concertId) {
        String key = QueueKey.processingCounter(concertId);
        Long result = redisTemplate.opsForValue().decrement(key);

        if (result != null && result < 0) {
            log.error("카운터가 음수: concertId={}, current={} - 데이터 정합성 점검 필요", concertId, result);
        } else {
            log.debug("카운터 감소: concertId={}, current={}", concertId, result);
        }
    }

    /**
     * 현재 카운터 조회
     */
    public int getCount(Long concertId) {
        String key = QueueKey.processingCounter(concertId);
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * 남은 입장 허용 인원 조회
     */
    public int remainingCapacity(Long concertId) {
        int current = getCount(concertId);
        return Math.max(0, maxProcessingCount - current);
    }

    /**
     * 카운터 일괄 증가 (분산락 내에서 사용)
     * 분산락이 보장된 상태에서 호출되어야 함
     */
    public void incrementBy(Long concertId, int count) {
        if (count <= 0) {
            return;
        }
        String key = QueueKey.processingCounter(concertId);
        Long result = redisTemplate.opsForValue().increment(key, count);
        log.debug("카운터 일괄 증가: concertId={}, delta={}, current={}", concertId, count, result);
    }

    /**
     * 카운터 초기화 (테스트용)
     */
    public void reset(Long concertId) {
        String key = QueueKey.processingCounter(concertId);
        redisTemplate.delete(key);
        log.info("카운터 초기화: concertId={}", concertId);
    }
}
