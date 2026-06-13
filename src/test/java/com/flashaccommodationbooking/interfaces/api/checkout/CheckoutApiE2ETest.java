package com.flashaccommodationbooking.interfaces.api.checkout;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.queue.QueueStatus;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CheckoutApiE2ETest extends IntegrationTest {

    private static final String ENDPOINT_CHECKOUT = "/checkout";
    private static final Long PRODUCT_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String ADMITTED_KEY = "queue:admitted:" + PRODUCT_ID;
    private static final String STOCK_KEY = "stock:" + PRODUCT_ID;
    private static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    private static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 2, 11, 0);

    private final TestRestTemplate testRestTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisCleanUp redisCleanUp;
    private final ProductJpaRepository productJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Autowired
    CheckoutApiE2ETest(
            TestRestTemplate testRestTemplate,
            StringRedisTemplate redisTemplate,
            RedisCleanUp redisCleanUp,
            ProductJpaRepository productJpaRepository,
            UserJpaRepository userJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
        this.productJpaRepository = productJpaRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @DisplayName("POST /checkout")
    @Nested
    class ReserveStock {

        @DisplayName("admitted 상태 queueToken과 재고가 있으면, 200과 checkoutToken을 반환하고 재고가 1 감소한다.")
        @Test
        void returnsCheckoutToken_whenAdmittedAndStockAvailable() {
            // arrange
            initStock(PRODUCT_ID, 1);
            String queueToken = createAdmittedQueueToken(USER_ID, PRODUCT_ID);

            CheckoutV1Dto.CheckoutRequest request = new CheckoutV1Dto.CheckoutRequest(queueToken);

            // act
            ResponseEntity<CommonApiResponse<CheckoutV1Dto.CheckoutResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            String checkoutToken = response.getBody().getData().checkoutToken();
            assertAll(
                    () -> assertThat(response.getBody().getCode()).isEqualTo("SUCCESS"),
                    () -> assertThat(checkoutToken).isNotBlank(),
                    () -> assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("0"),
                    () -> assertThat(redisTemplate.opsForSet().isMember(ADMITTED_KEY, queueToken)).isFalse(),
                    () -> assertThat(redisTemplate.opsForValue().get("checkout:" + checkoutToken))
                            .isEqualTo(USER_ID + ":" + PRODUCT_ID)
            );
        }

        @DisplayName("queueToken이 blank이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenQueueTokenIsBlank() {
            // arrange
            String requestBody = """
                    {
                        "queueToken": ""
                    }
                    """;

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("존재하지 않거나 만료된 queueToken이면, 404 QUEUE_TOKEN_NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenQueueTokenInvalid() {
            // arrange
            CheckoutV1Dto.CheckoutRequest request = new CheckoutV1Dto.CheckoutRequest(UUID.randomUUID().toString());

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("QUEUE_TOKEN_NOT_FOUND");
        }

        @DisplayName("WAITING 상태 queueToken이면, 403 CHECKOUT_NOT_ADMITTED를 반환한다.")
        @Test
        void returnsForbidden_whenQueueTokenNotAdmitted() {
            // arrange
            initStock(PRODUCT_ID, 1);
            String queueToken = createWaitingQueueToken(USER_ID, PRODUCT_ID);
            CheckoutV1Dto.CheckoutRequest request = new CheckoutV1Dto.CheckoutRequest(queueToken);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CHECKOUT_NOT_ADMITTED");
            assertThat(redisTemplate.opsForValue().get(STOCK_KEY)).isEqualTo("1");
        }

        @DisplayName("admitted이나 재고가 0이면, 409 PRODUCT_OUT_OF_STOCK을 반환한다.")
        @Test
        void returnsConflict_whenStockIsZero() {
            // arrange
            initStock(PRODUCT_ID, 0);
            String queueToken = createAdmittedQueueToken(USER_ID, PRODUCT_ID);
            CheckoutV1Dto.CheckoutRequest request = new CheckoutV1Dto.CheckoutRequest(queueToken);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("PRODUCT_OUT_OF_STOCK");
            assertThat(redisTemplate.opsForSet().isMember(ADMITTED_KEY, queueToken)).isTrue();
        }
    }

    @DisplayName("GET /checkout/{checkoutToken}")
    @Nested
    class GetOrderSheet {

        @DisplayName("유효한 checkoutToken이면, 200과 product + point + checkoutToken을 반환한다.")
        @Test
        void returnsOrderSheet_whenCheckoutTokenIsValid() {
            // arrange
            User user = userJpaRepository.save(User.of("test-user", 150_000));
            AccommodationProduct product = productJpaRepository.save(
                    AccommodationProduct.of("강남 호텔", 200_000, CHECK_IN, CHECK_OUT, LocalDateTime.now().minusHours(1), 10)
            );
            String checkoutToken = UUID.randomUUID().toString();
            saveCheckoutToken(checkoutToken, user.getId(), product.getId());

            // act
            ResponseEntity<CommonApiResponse<CheckoutV1Dto.OrderSheetResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT + "/" + checkoutToken,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            CheckoutV1Dto.OrderSheetResponse data = response.getBody().getData();
            assertAll(
                    () -> assertThat(response.getBody().getCode()).isEqualTo("SUCCESS"),
                    () -> assertThat(data.checkoutToken()).isEqualTo(checkoutToken),
                    () -> assertThat(data.point()).isEqualTo(150_000),
                    () -> assertThat(data.product().id()).isEqualTo(product.getId()),
                    () -> assertThat(data.product().name()).isEqualTo("강남 호텔"),
                    () -> assertThat(data.product().price()).isEqualTo(200_000),
                    () -> assertThat(data.product().checkIn()).isEqualTo(CHECK_IN),
                    () -> assertThat(data.product().checkOut()).isEqualTo(CHECK_OUT)
            );
        }

        @DisplayName("존재하지 않거나 만료된 checkoutToken이면, 404 CHECKOUT_TOKEN_NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenCheckoutTokenInvalid() {
            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT + "/" + UUID.randomUUID(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CHECKOUT_TOKEN_NOT_FOUND");
        }

        @DisplayName("checkoutToken은 유효하나 productId가 DB에 없으면, 404 PRODUCT_NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductNotFound() {
            // arrange
            User user = userJpaRepository.save(User.of("test-user", 150_000));
            String checkoutToken = UUID.randomUUID().toString();
            saveCheckoutToken(checkoutToken, user.getId(), 9_999L);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT + "/" + checkoutToken,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("PRODUCT_NOT_FOUND");
        }

        @DisplayName("checkoutToken은 유효하나 userId가 DB에 없으면, 404 USER_NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenUserNotFound() {
            // arrange
            AccommodationProduct product = productJpaRepository.save(
                    AccommodationProduct.of("강남 호텔", 200_000, CHECK_IN, CHECK_OUT, LocalDateTime.now().minusHours(1), 10)
            );
            String checkoutToken = UUID.randomUUID().toString();
            saveCheckoutToken(checkoutToken, 9_999L, product.getId());

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_CHECKOUT + "/" + checkoutToken,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("USER_NOT_FOUND");
        }
    }

    private void saveCheckoutToken(String checkoutToken, Long userId, Long productId) {
        redisTemplate.opsForValue().set("checkout:" + checkoutToken, userId + ":" + productId);
    }

    private void initStock(Long productId, int stock) {
        redisTemplate.opsForValue().set("stock:" + productId, String.valueOf(stock));
    }

    private String createAdmittedQueueToken(Long userId, Long productId) {
        String queueToken = UUID.randomUUID().toString();
        saveQueueTokenInfo(queueToken, userId, productId, QueueStatus.ADMITTED);
        redisTemplate.opsForSet().add("queue:admitted:" + productId, queueToken);
        return queueToken;
    }

    private String createWaitingQueueToken(Long userId, Long productId) {
        String queueToken = UUID.randomUUID().toString();
        saveQueueTokenInfo(queueToken, userId, productId, QueueStatus.WAITING);
        return queueToken;
    }

    private void saveQueueTokenInfo(String queueToken, Long userId, Long productId, QueueStatus status) {
        Map<String, String> fields = new HashMap<>();
        fields.put("userId", String.valueOf(userId));
        fields.put("productId", String.valueOf(productId));
        fields.put("status", status.name());
        fields.put("createdAt", String.valueOf(System.currentTimeMillis()));
        redisTemplate.opsForHash().putAll("queue:token:" + queueToken, fields);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
