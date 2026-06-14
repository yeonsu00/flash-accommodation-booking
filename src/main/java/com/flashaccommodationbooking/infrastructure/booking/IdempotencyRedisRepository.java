package com.flashaccommodationbooking.infrastructure.booking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class IdempotencyRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "idempotency:";
    private static final String PROCESSING = "processing";
    private static final long TTL_SECONDS = 86400L;

    public boolean setProcessing(String key) {
        Boolean result = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + key, PROCESSING, TTL_SECONDS, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    public Optional<Long> getBookingId(String key) {
        String value = redisTemplate.opsForValue().get(PREFIX + key);
        if (value == null || PROCESSING.equals(value)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    public void saveResult(String key, Long bookingId) {
        redisTemplate.opsForValue().set(PREFIX + key, String.valueOf(bookingId), TTL_SECONDS, TimeUnit.SECONDS);
    }

    public void delete(String key) {
        redisTemplate.delete(PREFIX + key);
    }

}
