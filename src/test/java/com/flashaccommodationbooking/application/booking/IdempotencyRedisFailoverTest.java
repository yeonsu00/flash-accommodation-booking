package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.domain.checkout.CheckoutToken;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.booking.BookingJpaRepository;
import com.flashaccommodationbooking.infrastructure.booking.IdempotencyRedisRepository;
import com.flashaccommodationbooking.infrastructure.checkout.CheckoutTokenJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PgPaymentSimulator;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.interfaces.listener.BookingEventListener;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import com.flashaccommodationbooking.support.utils.DatabaseCleanUp;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("멱등성 Redis 장애 DB UNIQUE 폴백 테스트")
class IdempotencyRedisFailoverTest extends IntegrationTest {

    private static final String CHECKOUT_TOKEN = "checkout-token-idempotency-failover";

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private CheckoutTokenJpaRepository checkoutTokenJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @MockitoSpyBean
    private IdempotencyRedisRepository idempotencyRedisRepository;

    @MockitoSpyBean
    private PgPaymentSimulator pgPaymentSimulator;

    @MockitoSpyBean
    private BookingEventListener bookingEventListener;

    private User user;
    private AccommodationProduct product;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
        reset(idempotencyRedisRepository, pgPaymentSimulator, bookingEventListener);

        user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
        saveCheckoutToken(CHECKOUT_TOKEN, user.getId(), product.getId());
        checkoutTokenJpaRepository.save(CheckoutToken.of(CHECKOUT_TOKEN, user.getId(), product.getId()));

        doReturn(UUID.randomUUID().toString()).when(pgPaymentSimulator).approve(any());
        doThrow(new RuntimeException("redis down")).when(idempotencyRedisRepository).setProcessing(anyString());
        doNothing().when(bookingEventListener).handleBookingCompleted(any());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAll();
        redisCleanUp.truncateAll();
    }

    @DisplayName("Redis 장애 시 setIdempotencyProcessing()이 true를 반환하고 요청을 허용한다")
    @Test
    void allowsRequest_whenRedisFails() {
        // act
        boolean allowed = bookingService.setIdempotencyProcessing("idempotency-key-1");

        // assert
        assertThat(allowed).isTrue();
    }

    @DisplayName("Redis 장애 상태에서 최초 요청은 정상적으로 예약을 생성한다")
    @Test
    void createsBookingNormally_whenFirstRequest_withRedisFail() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey);

        // act
        BookingInfo.BookingResult result = bookingFacade.createBooking(command);

        // assert
        assertThat(result.bookingId()).isNotNull();
        assertThat(bookingJpaRepository.count()).isEqualTo(1);
        assertThat(paymentJpaRepository.findAll().getFirst().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @DisplayName("Redis 장애 중 동일 idempotencyKey 재요청 시 DUPLICATE_PAYMENT(409) 예외가 발생한다")
    @Test
    void throwsDuplicatePayment_whenSameIdempotencyKeyRetried_withRedisFail() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey);

        bookingFacade.createBooking(command);

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                bookingFacade.createBooking(command)
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_PAYMENT);
        assertThat(bookingJpaRepository.count()).isEqualTo(1);
        assertThat(paymentJpaRepository.count()).isEqualTo(1);
    }

    @DisplayName("Redis 장애 중 동시 요청 2개를 내도 Payment는 1건만 생성된다")
    @Test
    void bookingCountIsOne_afterTwoConcurrentRequests_withRedisFail() throws Exception {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        BookingCommand.BookingRequestCommand command = createCommand(idempotencyKey);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<BookingInfo.BookingResult>> futures = new ArrayList<>();
        AtomicInteger duplicatePaymentCount = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            futures.add(executorService.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    return bookingFacade.createBooking(command);
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.DUPLICATE_PAYMENT) {
                        duplicatePaymentCount.incrementAndGet();
                        return null;
                    }
                    throw e;
                }
            }));
        }

        readyLatch.await();
        startLatch.countDown();

        List<Long> bookingIds = new ArrayList<>();
        for (Future<BookingInfo.BookingResult> future : futures) {
            BookingInfo.BookingResult result = future.get(10, TimeUnit.SECONDS);
            if (result != null) {
                bookingIds.add(result.bookingId());
            }
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        // assert
        assertThat(bookingJpaRepository.count()).isEqualTo(1);
        assertThat(paymentJpaRepository.count()).isEqualTo(1);
        assertThat(duplicatePaymentCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(bookingIds).isNotEmpty();
        assertThat(bookingIds).allMatch(id -> id.equals(bookingIds.getFirst()));
    }

    private BookingCommand.BookingRequestCommand createCommand(String idempotencyKey) {
        return new BookingCommand.BookingRequestCommand(
                CHECKOUT_TOKEN,
                idempotencyKey,
                List.of(BookingTestFixtures.creditCardMethod())
        );
    }

    private void saveCheckoutToken(String checkoutToken, Long userId, Long productId) {
        redisTemplate.opsForValue().set("checkout:" + checkoutToken, userId + ":" + productId);
    }
}
