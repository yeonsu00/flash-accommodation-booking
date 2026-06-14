package com.flashaccommodationbooking.interfaces.api.booking;

import com.flashaccommodationbooking.domain.booking.BookingStatus;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.common.CommonApiResponse;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.DatabaseCleanUp;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BookingV1ApiE2ETest extends IntegrationTest {

    private static final String ENDPOINT = "/bookings";

    private final TestRestTemplate testRestTemplate;
    private final StringRedisTemplate redisTemplate;
    private final RedisCleanUp redisCleanUp;
    private final DatabaseCleanUp databaseCleanUp;
    private final UserJpaRepository userJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    private String checkoutToken;
    private User user;
    private AccommodationProduct product;

    @Autowired
    BookingV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            StringRedisTemplate redisTemplate,
            RedisCleanUp redisCleanUp,
            DatabaseCleanUp databaseCleanUp,
            UserJpaRepository userJpaRepository,
            ProductJpaRepository productJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.redisTemplate = redisTemplate;
        this.redisCleanUp = redisCleanUp;
        this.databaseCleanUp = databaseCleanUp;
        this.userJpaRepository = userJpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();

        user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
        checkoutToken = UUID.randomUUID().toString();
        saveCheckoutToken(checkoutToken, user.getId(), product.getId());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
    }

    @DisplayName("해피 케이스")
    @Nested
    class HappyCase {

        @DisplayName("신용카드 단독 결제 시 200과 bookingId를 반환한다")
        @Test
        void returnsBookingId_whenCreditCardPayment() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = bookingRequestBody(checkoutToken, creditCardOnlyBody());

            // act
            ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> response = exchange(
                    requestBody,
                    idempotencyKey
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertAll(
                    () -> assertThat(response.getBody().getCode()).isEqualTo("SUCCESS"),
                    () -> assertThat(response.getBody().getData().bookingId()).isNotNull(),
                    () -> assertThat(response.getBody().getData().status()).isEqualTo(BookingStatus.CONFIRMED)
            );
        }

        @DisplayName("Y포인트 단독 결제 시 200과 bookingId를 반환한다")
        @Test
        void returnsBookingId_whenYPointPayment() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = bookingRequestBody(checkoutToken, yPointOnlyBody());

            // act
            ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> response = exchange(
                    requestBody,
                    idempotencyKey
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData().bookingId()).isNotNull();
        }

        @DisplayName("Y포인트 + 신용카드 복합 결제 시 200과 bookingId를 반환한다")
        @Test
        void returnsBookingId_whenCompositePayment() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = bookingRequestBody(checkoutToken, compositeBody());

            // act
            ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> response = exchange(
                    requestBody,
                    idempotencyKey
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData().bookingId()).isNotNull();
        }

        @DisplayName("동일 멱등키로 재요청 시 200과 동일 bookingId를 반환한다")
        @Test
        void returnsSameBookingId_whenSameIdempotencyKeyRetried() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            String requestBody = bookingRequestBody(checkoutToken, creditCardOnlyBody());

            ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> firstResponse = exchange(
                    requestBody,
                    idempotencyKey
            );

            // act
            ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> secondResponse = exchange(
                    requestBody,
                    idempotencyKey
            );

            // assert
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondResponse.getBody()).isNotNull();
            assertThat(secondResponse.getBody().getData().bookingId())
                    .isEqualTo(firstResponse.getBody().getData().bookingId());
        }
    }

    @DisplayName("예외 케이스")
    @Nested
    class ExceptionCase {

        @DisplayName("Idempotency-Key 헤더 누락 시 400을 반환한다")
        @Test
        void returnsBadRequest_whenIdempotencyKeyMissing() {
            // arrange
            String requestBody = bookingRequestBody(checkoutToken, creditCardOnlyBody());

            // act
            ResponseEntity<CommonApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("checkoutToken 누락 시 400을 반환한다")
        @Test
        void returnsBadRequest_whenCheckoutTokenMissing() {
            // arrange
            String requestBody = """
                    {
                        "checkoutToken": "",
                        "paymentMethods": [
                            { "methodType": "CREDIT_CARD", "amount": 100000 }
                        ]
                    }
                    """;

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("paymentMethods 누락 시 400을 반환한다")
        @Test
        void returnsBadRequest_whenPaymentMethodsMissing() {
            // arrange
            String requestBody = """
                    {
                        "checkoutToken": "%s",
                        "paymentMethods": []
                    }
                    """.formatted(checkoutToken);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("BAD_REQUEST");
        }

        @DisplayName("유효하지 않은 methodType 문자열이면 INVALID_PAYMENT_METHOD 400을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidPaymentMethod() {
            // arrange
            String requestBody = bookingRequestBody(checkoutToken, """
                    [
                        { "methodType": "INVALID", "amount": 100000 }
                    ]
                    """);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAYMENT_METHOD");
        }

        @DisplayName("신용카드 + Y페이 혼용 시 INVALID_PAYMENT_COMBINATION 400을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidPaymentCombination() {
            // arrange
            String requestBody = bookingRequestBody(checkoutToken, """
                    [
                        { "methodType": "CREDIT_CARD", "amount": 50000 },
                        { "methodType": "Y_PAY", "amount": 50000 }
                    ]
                    """);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAYMENT_COMBINATION");
        }

        @DisplayName("결제 금액 합계 불일치 시 INVALID_PAYMENT_AMOUNT 400을 반환한다")
        @Test
        void returnsBadRequest_whenPaymentAmountMismatch() {
            // arrange
            String requestBody = bookingRequestBody(checkoutToken, """
                    [
                        { "methodType": "CREDIT_CARD", "amount": 50000 }
                    ]
                    """);

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("INVALID_PAYMENT_AMOUNT");
        }

        @DisplayName("만료되거나 없는 checkoutToken이면 CHECKOUT_TOKEN_NOT_FOUND 404를 반환한다")
        @Test
        void returnsNotFound_whenCheckoutTokenInvalid() {
            // arrange
            String requestBody = bookingRequestBody(UUID.randomUUID().toString(), creditCardOnlyBody());

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("CHECKOUT_TOKEN_NOT_FOUND");
        }

        @DisplayName("Y포인트 잔액 부족 시 INSUFFICIENT_POINT 400을 반환한다")
        @Test
        void returnsBadRequest_whenInsufficientPoint() {
            // arrange
            databaseCleanUp.truncateAll();
            redisCleanUp.truncateAll();

            User lowPointUser = userJpaRepository.save(BookingTestFixtures.lowPointUser());
            AccommodationProduct savedProduct = productJpaRepository.save(BookingTestFixtures.defaultProduct());
            String lowPointCheckoutToken = UUID.randomUUID().toString();
            saveCheckoutToken(lowPointCheckoutToken, lowPointUser.getId(), savedProduct.getId());

            String requestBody = bookingRequestBody(lowPointCheckoutToken, yPointOnlyBody());

            // act
            ResponseEntity<CommonApiResponse<Void>> response = exchangeBadRequest(requestBody);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCode()).isEqualTo("INSUFFICIENT_POINT");
        }
    }

    private ResponseEntity<CommonApiResponse<BookingV1Dto.BookingResponse>> exchange(
            String requestBody,
            String idempotencyKey
    ) {
        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", idempotencyKey);

        return testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<CommonApiResponse<Void>> exchangeBadRequest(String requestBody) {
        HttpHeaders headers = jsonHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());

        return testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    private String bookingRequestBody(String token, String paymentMethodsJson) {
        return """
                {
                    "checkoutToken": "%s",
                    "paymentMethods": %s
                }
                """.formatted(token, paymentMethodsJson);
    }

    private String creditCardOnlyBody() {
        return """
                [
                    { "methodType": "CREDIT_CARD", "amount": 100000 }
                ]
                """;
    }

    private String yPointOnlyBody() {
        return """
                [
                    { "methodType": "Y_POINT", "amount": 100000 }
                ]
                """;
    }

    private String compositeBody() {
        return """
                [
                    { "methodType": "Y_POINT", "amount": 30000 },
                    { "methodType": "CREDIT_CARD", "amount": 70000 }
                ]
                """;
    }

    private void saveCheckoutToken(String checkoutToken, Long userId, Long productId) {
        redisTemplate.opsForValue().set("checkout:" + checkoutToken, userId + ":" + productId);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
