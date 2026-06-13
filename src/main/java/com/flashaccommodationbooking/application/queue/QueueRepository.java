package com.flashaccommodationbooking.application.queue;

import java.util.Optional;

public interface QueueRepository {

    void addToWaitingQueue(Long productId, String queueToken, double score);

    void saveQueueTokenInfo(String queueToken, Long userId, Long productId, long receivedAt);

    Optional<QueueInfo.TokenInfo> getQueueTokenInfo(String queueToken);

    Long getQueueRank(Long productId, String queueToken);

}
