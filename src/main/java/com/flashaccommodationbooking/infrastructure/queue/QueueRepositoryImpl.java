package com.flashaccommodationbooking.infrastructure.queue;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueRepository;
import com.flashaccommodationbooking.domain.queue.QueueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class QueueRepositoryImpl implements QueueRepository {

    private final QueueRedisRepository queueRedisRepository;

    @Override
    public void addToWaitingQueue(Long productId, String queueToken, double score) {
        queueRedisRepository.addToWaitingQueue(productId, queueToken, score);
    }

    @Override
    public void saveQueueTokenInfo(String queueToken, Long userId, Long productId, long receivedAt) {
        queueRedisRepository.saveQueueTokenInfo(queueToken, userId, productId, receivedAt);
    }

    @Override
    public Optional<QueueInfo.TokenInfo> getQueueTokenInfo(String queueToken) {
        Map<Object, Object> fields = queueRedisRepository.getQueueTokenInfo(queueToken);
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(QueueInfo.TokenInfo.from(fields));
    }

    @Override
    public Long getQueueRank(Long productId, String queueToken) {
        return queueRedisRepository.getQueueRank(productId, queueToken);
    }

    @Override
    public List<Long> getOpenedProductIds() {
        return queueRedisRepository.getOpenedProductIds();
    }

    @Override
    public Set<String> getWaitingTokens(Long productId, int count) {
        return queueRedisRepository.getWaitingTokens(productId, count);
    }

    @Override
    public void admitTokens(Long productId, Set<String> tokens) {
        queueRedisRepository.admitTokens(productId, tokens);
    }

    @Override
    public void updateTokenStatus(String queueToken, QueueStatus status) {
        queueRedisRepository.updateTokenStatus(queueToken, status.name());
    }

    @Override
    public void removeFromWaitingQueue(Long productId, Set<String> tokens) {
        queueRedisRepository.removeFromWaitingQueue(productId, tokens);
    }

}
