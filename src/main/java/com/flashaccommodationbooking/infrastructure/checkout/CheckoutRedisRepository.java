package com.flashaccommodationbooking.infrastructure.checkout;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

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

    public long reserveStock(Long productId, Long userId, String checkoutToken) {
        List<String> keys = List.of(
                STOCK_PREFIX + productId,
                CHECKOUT_PREFIX + checkoutToken
        );
        String value = userId + ":" + productId;
        return redisTemplate.execute(RESERVE_STOCK_SCRIPT, keys, value, String.valueOf(CHECKOUT_TTL_SECONDS));
    }

}
