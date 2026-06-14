package com.flashaccommodationbooking.infrastructure.booking;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
public class IdempotencyRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "idempotency:";
    private static final String PROCESSING = "processing";
    private static final long TTL_SECONDS = 86400L;

    @CircuitBreaker(name = "redis", fallbackMethod = "setProcessingFallback")
    public boolean setProcessing(String key) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + key, PROCESSING, TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "getBookingIdFallback")
    public Optional<Long> getBookingId(String key) {
        String value = redisTemplate.opsForValue().get(PREFIX + key);
        if (value == null || PROCESSING.equals(value)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "saveResultFallback")
    public void saveResult(String key, Long bookingId) {
        redisTemplate.opsForValue().set(PREFIX + key, String.valueOf(bookingId), TTL_SECONDS, TimeUnit.SECONDS);
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "deleteFallback")
    public void delete(String key) {
        redisTemplate.delete(PREFIX + key);
    }

    private boolean setProcessingFallback(String key, Exception e) {
        log.error("Redis 장애 - 멱등성 처리 불가 [key: {}, reason: {}]", key, e.getMessage());
        throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    private Optional<Long> getBookingIdFallback(String key, Exception e) {
        log.error("Redis 장애 - 멱등키 조회 불가 [key: {}, reason: {}]", key, e.getMessage());
        throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    private void saveResultFallback(String key, Long bookingId, Exception e) {
        log.error("Redis 장애 - 멱등키 결과 저장 불가 [key: {}, bookingId: {}, reason: {}]", key, bookingId, e.getMessage());
    }

    private void deleteFallback(String key, Exception e) {
        log.warn("Redis 장애 - 멱등키 삭제 불가 [key: {}, reason: {}]", key, e.getMessage());
    }
}
