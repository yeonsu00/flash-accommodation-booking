package com.flashaccommodationbooking.application.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class QueueServiceIntegrationTest {

    private static final Long PRODUCT_ID = 100L;
    private static final String WAITING_QUEUE_KEY = "queue:waiting:" + PRODUCT_ID;

    @Autowired
    private QueueService queueService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("enterQueue()")
    @Nested
    class EnterQueue {

        @DisplayName("대기열에 등록하면, Redis에 대기열 키와 토큰 정보 키가 저장된다.")
        @Test
        void savesTokenToRedis_whenEnterQueue() {
            // arrange
            long receivedAt = 1_700_000_000_000L;

            // act
            String queueToken = queueService.enterQueue(1L, PRODUCT_ID, receivedAt);

            // assert
            Double score = redisTemplate.opsForZSet().score(WAITING_QUEUE_KEY, queueToken);
            Map<Object, Object> tokenInfo = redisTemplate.opsForHash().entries("queue:token:" + queueToken);

            assertAll(
                    () -> assertThat(score).isEqualTo((double) receivedAt),
                    () -> assertThat(tokenInfo).containsEntry("userId", "1"),
                    () -> assertThat(tokenInfo).containsEntry("productId", String.valueOf(PRODUCT_ID)),
                    () -> assertThat(tokenInfo).containsEntry("status", QueueStatus.WAITING.name()),
                    () -> assertThat(tokenInfo).containsEntry("createdAt", String.valueOf(receivedAt))
            );
        }

        @DisplayName("두 사용자가 대기열에 등록하면, 각각 다른 토큰을 발급받는다.")
        @Test
        void assignsDifferentTokens_whenTwoUsersEnterQueue() {
            // act
            String token1 = queueService.enterQueue(1L, PRODUCT_ID, 1_000L);
            String token2 = queueService.enterQueue(2L, PRODUCT_ID, 2_000L);

            // assert
            assertThat(token1).isNotEqualTo(token2);
        }

        @DisplayName("여러 사용자가 순서대로 등록하면, 먼저 등록한 사용자의 순번이 더 낮다.")
        @Test
        void ranksInReceivedOrder_whenMultipleUsersEnter() {
            // act
            String firstToken = queueService.enterQueue(1L, PRODUCT_ID, 1_000L);
            String secondToken = queueService.enterQueue(2L, PRODUCT_ID, 2_000L);
            String thirdToken = queueService.enterQueue(3L, PRODUCT_ID, 3_000L);

            // assert
            assertAll(
                    () -> assertThat(queueService.getStatus(firstToken).rank()).isEqualTo(1L),
                    () -> assertThat(queueService.getStatus(secondToken).rank()).isEqualTo(2L),
                    () -> assertThat(queueService.getStatus(thirdToken).rank()).isEqualTo(3L)
            );
        }
    }

    @DisplayName("getStatus()")
    @Nested
    class GetStatus {

        @DisplayName("등록 직후 상태를 조회하면, WAITING 상태와 순번 1을 반환한다.")
        @Test
        void returnsWaitingWithCorrectRank_whenPollingStatus() {
            // arrange
            String queueToken = queueService.enterQueue(1L, PRODUCT_ID, 1_000L);

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(queueToken);

            // assert
            assertAll(
                    () -> assertThat(statusInfo.status()).isEqualTo(QueueStatus.WAITING),
                    () -> assertThat(statusInfo.rank()).isEqualTo(1L)
            );
        }

        @DisplayName("Redis에서 토큰 상태를 ADMITTED로 변경하면, ADMITTED 상태를 반환한다.")
        @Test
        void returnsAdmitted_whenTokenStatusUpdatedToAdmitted() {
            // arrange
            String queueToken = queueService.enterQueue(1L, PRODUCT_ID, 1_000L);
            redisTemplate.opsForHash().put("queue:token:" + queueToken, "status", QueueStatus.ADMITTED.name());

            // act
            QueueInfo.StatusInfo statusInfo = queueService.getStatus(queueToken);

            // assert
            assertAll(
                    () -> assertThat(statusInfo.status()).isEqualTo(QueueStatus.ADMITTED),
                    () -> assertThat(statusInfo.rank()).isNull()
            );
        }

        @DisplayName("존재하지 않는 토큰으로 조회하면, QUEUE_TOKEN_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenTokenExpiredOrNotExist() {
            // arrange
            String nonExistentToken = UUID.randomUUID().toString();

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                queueService.getStatus(nonExistentToken);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_NOT_FOUND);
        }
    }
}
