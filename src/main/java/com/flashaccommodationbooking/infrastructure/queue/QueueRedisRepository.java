package com.flashaccommodationbooking.infrastructure.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class QueueRedisRepository {

    private final StringRedisTemplate redisTemplate;

    private static final String WAITING_QUEUE_PREFIX = "queue:waiting:";
    private static final String TOKEN_INFO_PREFIX = "queue:token:";
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

}
