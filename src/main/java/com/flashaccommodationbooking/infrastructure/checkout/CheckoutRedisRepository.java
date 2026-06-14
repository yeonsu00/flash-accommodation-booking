package com.flashaccommodationbooking.infrastructure.checkout;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CheckoutRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String STOCK_PREFIX = "stock:";
    private static final String CHECKOUT_PREFIX = "checkout:";
    private static final long CHECKOUT_TTL_SECONDS = 300L;

    private static final DefaultRedisScript<Long> RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil or stock <= 0 then
                return -1
            end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            return 1
            """, Long.class);

    @CircuitBreaker(name = "redis", fallbackMethod = "getCheckoutTokenValueFallback")
    public Optional<String> getCheckoutTokenValue(String checkoutToken) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CHECKOUT_PREFIX + checkoutToken));
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "reserveStockFallback")
    public long reserveStock(Long productId, Long userId, String checkoutToken) {
        List<String> keys = List.of(
                STOCK_PREFIX + productId,
                CHECKOUT_PREFIX + checkoutToken
        );
        String value = userId + ":" + productId;
        return redisTemplate.execute(RESERVE_STOCK_SCRIPT, keys, value, String.valueOf(CHECKOUT_TTL_SECONDS));
    }

    @CircuitBreaker(name = "redis", fallbackMethod = "deleteCheckoutTokenFallback")
    public void deleteCheckoutToken(String checkoutToken) {
        redisTemplate.delete(CHECKOUT_PREFIX + checkoutToken);
    }

    private Optional<String> getCheckoutTokenValueFallback(String checkoutToken, Exception e) {
        log.error("Redis 장애 - checkoutToken 조회 불가 [token: {}, reason: {}]", checkoutToken, e.getMessage());
        throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    private long reserveStockFallback(Long productId, Long userId, String checkoutToken, Exception e) {
        log.error("Redis 장애 - 재고 차감 불가 [productId: {}, reason: {}]", productId, e.getMessage());
        throw new BusinessException(ErrorCode.REDIS_UNAVAILABLE);
    }

    private void deleteCheckoutTokenFallback(String checkoutToken, Exception e) {
        log.warn("Redis 장애 - checkoutToken 삭제 불가 [token: {}, reason: {}]", checkoutToken, e.getMessage());
    }
}
