package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.queue.QueueRedisRepository;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("대기열 Redis 장애 503 테스트")
class QueueRedisFailoverTest extends IntegrationTest {

    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private QueueRedisRepository queueRedisRepository;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
        reset(queueRedisRepository);
        saveOpenedProduct(PRODUCT_ID, 0L);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 진입 Redis 장애 시 REDIS_UNAVAILABLE(503)을 반환한다")
    @Test
    void returns503_whenRedisFailsOnQueueEnter() {
        // arrange
        doThrow(new RuntimeException("redis down"))
                .when(queueRedisRepository)
                .addToWaitingQueue(anyLong(), anyString(), anyDouble());

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                queueService.enterQueue(1L, PRODUCT_ID, System.currentTimeMillis())
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
    }

    @DisplayName("대기열 상태 조회 Redis 장애 시 REDIS_UNAVAILABLE(503)을 반환한다")
    @Test
    void returns503_whenRedisFailsOnQueueStatus() {
        // arrange
        String queueToken = queueService.enterQueue(1L, PRODUCT_ID, 1_700_000_000_000L);
        reset(queueRedisRepository);
        doThrow(new RuntimeException("redis down"))
                .when(queueRedisRepository)
                .getQueueTokenInfo(anyString());

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                queueService.getStatus(queueToken)
        );
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
    }

    private void saveOpenedProduct(Long productId, long openAt) {
        Map<String, String> fields = new HashMap<>();
        fields.put("openAt", String.valueOf(openAt));
        redisTemplate.opsForHash().putAll("open:" + productId, fields);
    }
}
