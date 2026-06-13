package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final int ADMISSION_BATCH_SIZE = 5;

    private final QueueRepository queueRepository;

    public String enterQueue(Long userId, Long productId, long receivedAt) {
        Long openAt = queueRepository.getProductOpenAt(productId);
        if (openAt == null || receivedAt < openAt) {
            throw new BusinessException(ErrorCode.QUEUE_NOT_OPEN);
        }

        String queueToken = UUID.randomUUID().toString();

        queueRepository.addToWaitingQueue(productId, queueToken, receivedAt);
        queueRepository.saveQueueTokenInfo(queueToken, userId, productId, receivedAt);

        return queueToken;
    }

    public QueueInfo.StatusInfo getStatus(String queueToken) {
        QueueInfo.TokenInfo tokenInfo = queueRepository.getQueueTokenInfo(queueToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUEUE_TOKEN_NOT_FOUND));

        if (tokenInfo.status() == QueueStatus.ADMITTED) {
            return QueueInfo.StatusInfo.admitted();
        }

        Long rank = queueRepository.getQueueRank(tokenInfo.productId(), queueToken);
        return QueueInfo.StatusInfo.waiting(rank != null ? rank + 1 : null);
    }

    public List<Long> getOpenedProductIds() {
        return queueRepository.getOpenedProductIds();
    }

    public void admitFromQueue(Long productId) {
        Set<String> tokens = queueRepository.getWaitingTokens(productId, ADMISSION_BATCH_SIZE);
        if (tokens.isEmpty()) {
            return;
        }

        queueRepository.admitTokens(productId, tokens);
        tokens.forEach(token -> queueRepository.updateTokenStatus(token, QueueStatus.ADMITTED));
        queueRepository.removeFromWaitingQueue(productId, tokens);
    }
}
