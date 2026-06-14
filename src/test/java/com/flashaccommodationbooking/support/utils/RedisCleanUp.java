package com.flashaccommodationbooking.support.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class RedisCleanUp {

    private final StringRedisTemplate redisTemplate;

    public RedisCleanUp(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void truncateAll() {
        deleteByPattern("queue:waiting:*");
        deleteByPattern("queue:token:*");
        deleteByPattern("queue:admitted:*");
        deleteByPattern("open:*");
        deleteByPattern("stock:*");
        deleteByPattern("checkout:*");
        deleteByPattern("idempotency:*");
    }

    private void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
