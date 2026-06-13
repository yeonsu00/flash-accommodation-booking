package com.flashaccommodationbooking.interfaces.scheduler;

import com.flashaccommodationbooking.application.queue.QueueService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class QueueAdmissionScheduler {

    private final RedissonClient redissonClient;
    private final QueueService queueService;

    @Scheduled(fixedDelay = 100)
    public void admitFromQueue() {
        List<Long> openedProductIds = queueService.getOpenedProductIds();
        if (openedProductIds.isEmpty()) {
            return;
        }

        for (Long productId : openedProductIds) {
            RLock lock = redissonClient.getLock("lock:queue:admission:" + productId);
            if (!lock.tryLock()) {
                continue;
            }
            try {
                queueService.admitFromQueue(productId);
            } finally {
                lock.unlock();
            }
        }
    }
}
