package com.flashaccommodationbooking.global;

import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.queue.QueueRedisRepository;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Redis CircuitBreaker 통합 테스트")
class RedisCircuitBreakerTest extends IntegrationTest {

    private static final Long PRODUCT_ID = 100L;

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private QueueRedisRepository queueRedisRepository;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
        saveOpenedProduct(PRODUCT_ID, 0L);

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        circuitBreaker.transitionToClosedState();
        circuitBreaker.reset();
        reset(queueRedisRepository);
    }

    @DisplayName("Circuit CLOSED 상태에서 Redis를 실제 호출한다")
    @Order(1)
    @Test
    void callsRedis_whenCircuitIsClosed() {
        // act
        String queueToken = queueService.enterQueue(1L, PRODUCT_ID, 1_700_000_000_000L);

        // assert
        assertThat(queueToken).isNotBlank();
        assertThat(circuitBreakerRegistry.circuitBreaker("redis").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @DisplayName("5회 중 3회 실패하면 Circuit OPEN되고 이후 호출은 즉시 fallback된다")
    @Order(2)
    @Test
    void opensCircuit_whenFailureRateExceedsThreshold() {
        // arrange
        doThrow(new RuntimeException("redis down"))
                .when(queueRedisRepository)
                .getProductOpenAt(anyLong());

        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class, () ->
                    queueService.enterQueue(1L, PRODUCT_ID, System.currentTimeMillis())
            );
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int invocationCount = org.mockito.Mockito.mockingDetails(queueRedisRepository).getInvocations().size();

        // act
        BusinessException exception = assertThrows(BusinessException.class, () ->
                queueService.enterQueue(1L, PRODUCT_ID, System.currentTimeMillis())
        );

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REDIS_UNAVAILABLE);
        assertThat(org.mockito.Mockito.mockingDetails(queueRedisRepository).getInvocations())
                .hasSize(invocationCount);
    }

    @DisplayName("OPEN 후 wait-duration 경과 시 HALF_OPEN을 거쳐 CLOSED로 복구된다")
    @Order(3)
    @Test
    void transitionsToHalfOpen_afterWaitDuration() throws InterruptedException {
        // arrange
        doThrow(new RuntimeException("redis down"))
                .when(queueRedisRepository)
                .getProductOpenAt(anyLong());

        for (int i = 0; i < 5; i++) {
            assertThrows(BusinessException.class, () ->
                    queueService.enterQueue(1L, PRODUCT_ID, System.currentTimeMillis())
            );
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("redis");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(300);
        reset(queueRedisRepository);

        // act
        String queueToken = queueService.enterQueue(1L, PRODUCT_ID, 1_700_000_000_000L);

        // assert
        assertThat(queueToken).isNotBlank();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private void saveOpenedProduct(Long productId, long openAt) {
        Map<String, String> fields = new HashMap<>();
        fields.put("openAt", String.valueOf(openAt));
        redisTemplate.opsForHash().putAll("open:" + productId, fields);
    }
}
