package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface QueueRepository {

    void addToWaitingQueue(Long productId, String queueToken, double score);

    void saveQueueTokenInfo(String queueToken, Long userId, Long productId, long receivedAt);

    Optional<QueueInfo.TokenInfo> getQueueTokenInfo(String queueToken);

    Long getQueueRank(Long productId, String queueToken);

    List<Long> getOpenedProductIds();

    Long getProductOpenAt(Long productId);

    Set<String> getWaitingTokens(Long productId, int count);

    void admitTokens(Long productId, Set<String> tokens);

    boolean isAdmitted(Long productId, String queueToken);

    void removeFromAdmitted(Long productId, String queueToken);

    void updateTokenStatus(String queueToken, QueueStatus status);

    void removeFromWaitingQueue(Long productId, Set<String> tokens);

}
