package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.application.booking.event.BookingCompletedEvent;
import com.flashaccommodationbooking.application.booking.event.BookingFailedEvent;
import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import com.flashaccommodationbooking.domain.booking.BookingStatus;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.booking.BookingJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentMethodDetailJpaRepository;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class BookingFacadeIntegrationTest extends IntegrationTest {

    private static final String CHECKOUT_TOKEN = "checkout-token-uuid";

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PaymentMethodDetailJpaRepository paymentMethodDetailJpaRepository;

    private User user;
    private AccommodationProduct product;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        redisCleanUp.truncateAll();

        user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
        saveCheckoutToken(CHECKOUT_TOKEN, user.getId(), product.getId());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        redisCleanUp.truncateAll();
    }

    @DisplayName("해피 케이스")
    @Nested
    class HappyCase {

        @DisplayName("정상 예약 시 BookingResult가 반환된다")
        @Test
        void returnsBookingResult_whenBookingSucceeds() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey, BookingTestFixtures.creditCardMethod());

            // act
            BookingInfo.BookingResult result = bookingFacade.createBooking(command);

            // assert
            assertThat(result.bookingId()).isNotNull();
            assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(bookingJpaRepository.count()).isEqualTo(1);
        }

        @DisplayName("동일 멱등키로 재요청 시 동일 bookingId가 반환된다 (멱등성)")
        @Test
        void returnsSameBookingId_whenSameIdempotencyKeyRetried() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey, BookingTestFixtures.creditCardMethod());
            BookingInfo.BookingResult firstResult = bookingFacade.createBooking(command);

            // act
            BookingInfo.BookingResult secondResult = bookingFacade.createBooking(command);

            // assert
            assertThat(secondResult.bookingId()).isEqualTo(firstResult.bookingId());
            assertThat(bookingJpaRepository.count()).isEqualTo(1);
        }

        @DisplayName("예약 성공 시 BookingCompletedEvent가 발행된다")
        @Test
        void publishesBookingCompletedEvent_whenBookingSucceeds() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey, BookingTestFixtures.creditCardMethod());

            // act
            bookingFacade.createBooking(command);

            // assert
            assertThat(applicationEvents.stream(BookingCompletedEvent.class)).hasSize(1);
        }
    }

    @DisplayName("예외 케이스")
    @Nested
    class ExceptionCase {

        @DisplayName("유효하지 않은 checkoutToken이면 CHECKOUT_TOKEN_NOT_FOUND 예외가 발생한다")
        @Test
        void throwsException_whenCheckoutTokenInvalid() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = new BookingCommand.BookingRequestCommand(
                    UUID.randomUUID().toString(),
                    idempotencyKey,
                    java.util.List.of(BookingTestFixtures.creditCardMethod())
            );

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                bookingFacade.createBooking(command);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CHECKOUT_TOKEN_NOT_FOUND);
        }

        @DisplayName("신용카드 + Y페이 혼용 시 INVALID_PAYMENT_COMBINATION 예외가 발생한다")
        @Test
        void throwsException_whenInvalidPaymentCombination() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(
                    idempotencyKey,
                    BookingTestFixtures.invalidCreditCardAndYPay()
            );

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                bookingFacade.createBooking(command);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }

        @DisplayName("결제 금액 합계 불일치 시 INVALID_PAYMENT_AMOUNT 예외가 발생한다")
        @Test
        void throwsException_whenPaymentAmountMismatch() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(
                    idempotencyKey,
                    java.util.List.of(new Method(PaymentMethodType.CREDIT_CARD, 50_000))
            );

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                bookingFacade.createBooking(command);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        @DisplayName("Y포인트 잔액 부족 시 INSUFFICIENT_POINT 예외가 발생하고 멱등키가 삭제된다")
        @Test
        void throwsExceptionAndDeletesIdempotencyKey_whenInsufficientPoint() {
            // arrange
            cleanDatabase();
            redisCleanUp.truncateAll();

            User lowPointUser = userJpaRepository.save(BookingTestFixtures.lowPointUser());
            product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
            saveCheckoutToken(CHECKOUT_TOKEN, lowPointUser.getId(), product.getId());

            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey, BookingTestFixtures.yPointMethod());

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                bookingFacade.createBooking(command);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
            assertThat(redisTemplate.hasKey("idempotency:" + idempotencyKey)).isFalse();
        }

        @DisplayName("실패 시 BookingFailedEvent가 발행된다")
        @Test
        void publishesBookingFailedEvent_whenBookingFails() {
            // arrange
            String idempotencyKey = UUID.randomUUID().toString();
            BookingCommand.BookingRequestCommand command = createCommand(
                    idempotencyKey,
                    BookingTestFixtures.invalidCreditCardAndYPay()
            );

            // act
            assertThrows(BusinessException.class, () -> bookingFacade.createBooking(command));

            // assert
            assertThat(applicationEvents.stream(BookingFailedEvent.class)).hasSize(1);
        }
    }

    private BookingCommand.BookingRequestCommand createCommand(
            String idempotencyKey,
            Method method
    ) {
        return new BookingCommand.BookingRequestCommand(
                CHECKOUT_TOKEN,
                idempotencyKey,
                java.util.List.of(method)
        );
    }

    private BookingCommand.BookingRequestCommand createCommand(
            String idempotencyKey,
            java.util.List<Method> methods
    ) {
        return new BookingCommand.BookingRequestCommand(
                CHECKOUT_TOKEN,
                idempotencyKey,
                methods
        );
    }

    private void saveCheckoutToken(String checkoutToken, Long userId, Long productId) {
        redisTemplate.opsForValue().set("checkout:" + checkoutToken, userId + ":" + productId);
    }

    private void cleanDatabase() {
        paymentMethodDetailJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }
}
