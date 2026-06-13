package com.flashaccommodationbooking.infrastructure.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_QUEUE_PREFIX = "queue:waiting:";
    private static final String ADMITTED_PREFIX = "queue:admitted:";
    private static final String TOKEN_INFO_PREFIX = "queue:token:";
    private static final String OPEN_PREFIX = "open:";
    private static final long TOKEN_TTL_SECONDS = 1800L;

    public void addToWaitingQueue(Long productId, String queueToken, double score) {
        redisTemplate.opsForZSet().add(WAITING_QUEUE_PREFIX + productId, queueToken, score);
    }

    public void saveQueueTokenInfo(String queueToken, Long userId, Long productId, long receivedAt) {
        String key = TOKEN_INFO_PREFIX + queueToken;
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("productId", String.valueOf(productId));
        fields.put("status", QueueStatus.WAITING.name());
        fields.put("createdAt", String.valueOf(receivedAt));
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
    }

    public Map<Object, Object> getQueueTokenInfo(String queueToken) {
        return redisTemplate.opsForHash().entries(TOKEN_INFO_PREFIX + queueToken);
    }

    public Long getQueueRank(Long productId, String queueToken) {
        return redisTemplate.opsForZSet().rank(WAITING_QUEUE_PREFIX + productId, queueToken);
    }

    public List<Long> getOpenedProductIds() {
        List<Long> productIds = new ArrayList<>();
        long now = System.currentTimeMillis();
        ScanOptions options = ScanOptions.scanOptions()
                .match(OPEN_PREFIX + "*")
                .count(100)
                .build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            cursor.forEachRemaining(key -> {
                Object value = redisTemplate.opsForHash().get(key, "openAt");
                if (value != null && now >= Long.parseLong((String) value)) {
                    productIds.add(Long.parseLong(key.substring(OPEN_PREFIX.length())));
                }
            });
        }
        return productIds;
    }

    public Long getProductOpenAt(Long productId) {
        Object value = redisTemplate.opsForHash().get(OPEN_PREFIX + productId, "openAt");
        return value != null ? Long.parseLong((String) value) : null;
    }

    public Set<String> getWaitingTokens(Long productId, int count) {
        Set<String> result = redisTemplate.opsForZSet().range(WAITING_QUEUE_PREFIX + productId, 0, count - 1);
        return result != null ? result : Collections.emptySet();
    }

    public void admitTokens(Long productId, Set<String> tokens) {
        redisTemplate.opsForSet().add(ADMITTED_PREFIX + productId, tokens.toArray(new String[0]));
    }

    public boolean isAdmitted(Long productId, String queueToken) {
        Boolean result = redisTemplate.opsForSet().isMember(ADMITTED_PREFIX + productId, queueToken);
        return Boolean.TRUE.equals(result);
    }

    public void removeFromAdmitted(Long productId, String queueToken) {
        redisTemplate.opsForSet().remove(ADMITTED_PREFIX + productId, (Object) queueToken);
    }

    public void updateTokenStatus(String queueToken, String status) {
        redisTemplate.opsForHash().put(TOKEN_INFO_PREFIX + queueToken, "status", status);
    }

    public void removeFromWaitingQueue(Long productId, Set<String> tokens) {
        redisTemplate.opsForZSet().remove(WAITING_QUEUE_PREFIX + productId, tokens.toArray());
    }

}
