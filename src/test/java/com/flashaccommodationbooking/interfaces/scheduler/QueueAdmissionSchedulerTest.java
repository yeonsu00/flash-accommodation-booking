package com.flashaccommodationbooking.interfaces.scheduler;

import com.flashaccommodationbooking.application.queue.QueueService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueAdmissionSchedulerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private QueueAdmissionScheduler queueAdmissionScheduler;

    private static final Long PRODUCT_ID_1 = 100L;
    private static final Long PRODUCT_ID_2 = 200L;

    @DisplayName("admitFromQueue()")
    @Nested
    class AdmitFromQueue {

        @DisplayName("오픈된 상품이 없으면, 입장 처리를 호출하지 않는다.")
        @Test
        void doesNothing_whenNoOpenedProducts() {
            // arrange
            when(queueService.getOpenedProductIds()).thenReturn(Collections.emptyList());

            // act
            queueAdmissionScheduler.admitFromQueue();

            // assert
            verify(redissonClient, never()).getLock(eq("lock:queue:admission:" + PRODUCT_ID_1));
            verify(queueService, never()).admitFromQueue(anyLong());
        }

        @DisplayName("오픈된 상품마다 분산 락을 획득하고 입장 처리를 호출한다.")
        @Test
        void admitsForEachOpenedProduct_whenLockAcquired() {
            // arrange
            RLock lock1 = mock(RLock.class);
            RLock lock2 = mock(RLock.class);
            when(queueService.getOpenedProductIds()).thenReturn(List.of(PRODUCT_ID_1, PRODUCT_ID_2));
            when(redissonClient.getLock("lock:queue:admission:" + PRODUCT_ID_1)).thenReturn(lock1);
            when(redissonClient.getLock("lock:queue:admission:" + PRODUCT_ID_2)).thenReturn(lock2);
            when(lock1.tryLock()).thenReturn(true);
            when(lock2.tryLock()).thenReturn(true);

            // act
            queueAdmissionScheduler.admitFromQueue();

            // assert
            verify(queueService).admitFromQueue(PRODUCT_ID_1);
            verify(queueService).admitFromQueue(PRODUCT_ID_2);
            verify(lock1).unlock();
            verify(lock2).unlock();
        }

        @DisplayName("분산 락 획득에 실패하면, 해당 상품의 입장 처리를 건너뛴다.")
        @Test
        void skipsProduct_whenLockNotAcquired() {
            // arrange
            RLock lock1 = mock(RLock.class);
            RLock lock2 = mock(RLock.class);
            when(queueService.getOpenedProductIds()).thenReturn(List.of(PRODUCT_ID_1, PRODUCT_ID_2));
            when(redissonClient.getLock("lock:queue:admission:" + PRODUCT_ID_1)).thenReturn(lock1);
            when(redissonClient.getLock("lock:queue:admission:" + PRODUCT_ID_2)).thenReturn(lock2);
            when(lock1.tryLock()).thenReturn(false);
            when(lock2.tryLock()).thenReturn(true);

            // act
            queueAdmissionScheduler.admitFromQueue();

            // assert
            verify(queueService, never()).admitFromQueue(PRODUCT_ID_1);
            verify(queueService).admitFromQueue(PRODUCT_ID_2);
            verify(lock1, never()).unlock();
            verify(lock2).unlock();
        }

        @DisplayName("입장 처리 중 예외가 발생해도, 분산 락을 해제한다.")
        @Test
        void unlocksLock_whenAdmitThrowsException() {
            // arrange
            RLock lock = mock(RLock.class);
            when(queueService.getOpenedProductIds()).thenReturn(List.of(PRODUCT_ID_1));
            when(redissonClient.getLock("lock:queue:admission:" + PRODUCT_ID_1)).thenReturn(lock);
            when(lock.tryLock()).thenReturn(true);
            doThrow(new RuntimeException("admit failed")).when(queueService).admitFromQueue(PRODUCT_ID_1);

            // act & assert
            assertThrows(RuntimeException.class, () -> queueAdmissionScheduler.admitFromQueue());
            verify(lock, times(1)).unlock();
        }
    }
}
