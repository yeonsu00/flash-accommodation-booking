package com.flashaccommodationbooking.interfaces.api.queue;

import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class QueueApiE2ETest extends IntegrationTest {

    private static final String ENDPOINT_ENTER = "/queue/enter";
    private static final String ENDPOINT_STATUS = "/queue/status";
    private static final Long PRODUCT_ID = 100L;

    private final TestRestTemplate testRestTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    QueueApiE2ETest(
            TestRestTemplate testRestTemplate,
            StringRedisTemplate redisTemplate,
            RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @BeforeEach
    void setUpOpenProduct() {
        saveOpenedProduct(PRODUCT_ID, 0L);
    }

    @DisplayName("POST /queue/enter")
    @Nested
    class EnterQueue {

        @DisplayName("정상 요청이면, 200과 대기열 토큰을 반환한다.")
        @Test
        void returnsQueueToken_whenEnterQueueSuccessfully() {
            // arrange
            QueueV1Dto.EnterRequest request = new QueueV1Dto.EnterRequest(1L, PRODUCT_ID);

            // act
            ResponseEntity<CommonApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("SUCCESS");
            assertThat(response.getBody().getData().queueToken()).isNotBlank();
        }

        @DisplayName("userId가 없으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenUserIdIsNull() {
            // arrange
            String requestBody = """
                    {
                        "productId": 100
                    }
                    """;

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("productId가 없으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenProductIdIsNull() {
            // arrange
            String requestBody = """
                    {
                        "userId": 1
                    }
                    """;

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("오픈 시간 이전이면, 400 QUEUE_NOT_OPEN을 반환한다.")
        @Test
        void returnsBadRequest_whenQueueNotOpen() {
            // arrange
            saveOpenedProduct(PRODUCT_ID, System.currentTimeMillis() + 60_000L);
            QueueV1Dto.EnterRequest request = new QueueV1Dto.EnterRequest(1L, PRODUCT_ID);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_ENTER,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("QUEUE_NOT_OPEN");
        }
    }

    @DisplayName("GET /queue/status")
    @Nested
    class GetStatus {

        @DisplayName("대기열 등록 직후 조회하면, 200과 WAITING 상태를 반환한다.")
        @Test
        void returnsWaitingStatus_whenPollingAfterEnter() {
            // arrange
            String queueToken = enterQueueAndGetToken(1L, PRODUCT_ID);

            // act
            ResponseEntity<CommonApiResponse<QueueV1Dto.StatusResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_STATUS + "?queueToken=" + queueToken,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertAll(
                    () -> assertThat(response.getBody().getCode()).isEqualTo("SUCCESS"),
                    () -> assertThat(response.getBody().getData().status()).isEqualTo(QueueStatus.WAITING),
                    () -> assertThat(response.getBody().getData().rank()).isEqualTo(1L)
            );
        }

        @DisplayName("입장 허가된 토큰으로 조회하면, 200과 ADMITTED 상태를 반환한다.")
        @Test
        void returnsAdmittedStatus_whenTokenAdmitted() {
            // arrange
            String queueToken = enterQueueAndGetToken(1L, PRODUCT_ID);
            redisTemplate.opsForHash().put("queue:token:" + queueToken, "status", QueueStatus.ADMITTED.name());

            // act
            ResponseEntity<CommonApiResponse<QueueV1Dto.StatusResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_STATUS + "?queueToken=" + queueToken,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertAll(
                    () -> assertThat(response.getBody().getData().status()).isEqualTo(QueueStatus.ADMITTED),
                    () -> assertThat(response.getBody().getData().rank()).isNull()
            );
        }

        @DisplayName("유효하지 않은 토큰으로 조회하면, 404 QUEUE_TOKEN_NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenQueueTokenInvalid() {
            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_STATUS + "?queueToken=invalid-token",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("QUEUE_TOKEN_NOT_FOUND");
        }

        @DisplayName("queueToken 파라미터가 없으면, 400을 반환한다.")
        @Test
        void returnsBadRequest_whenQueueTokenParamMissing() {
            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_STATUS,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }
    }

    private String enterQueueAndGetToken(Long userId, Long productId) {
        QueueV1Dto.EnterRequest request = new QueueV1Dto.EnterRequest(userId, productId);
        ResponseEntity<CommonApiResponse<QueueV1Dto.EnterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ENTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().getData().queueToken();
    }

    private void saveOpenedProduct(Long productId, long openAt) {
        redisTemplate.opsForHash().put("open:" + productId, "openAt", String.valueOf(openAt));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
