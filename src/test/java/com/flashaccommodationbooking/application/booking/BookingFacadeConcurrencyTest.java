package com.flashaccommodationbooking.application.booking;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

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

@SpringBootTest
@ActiveProfiles("test")
class BookingFacadeConcurrencyTest extends IntegrationTest {

    private static final String CHECKOUT_TOKEN = "checkout-token-concurrency";
    private static final int THREAD_COUNT = 10;

    @Autowired
    private BookingFacade bookingFacade;

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

    @BeforeEach
    void setUp() {
        cleanDatabase();
        redisCleanUp.truncateAll();

        User user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        AccommodationProduct product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
        saveCheckoutToken(CHECKOUT_TOKEN, user.getId(), product.getId());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
        redisCleanUp.truncateAll();
    }

    @DisplayName("동일 멱등키로 동시에 요청해도 Booking이 하나만 생성된다")
    @Test
    void createsOnlyOneBooking_whenSameIdempotencyKeyRequestedConcurrently() throws Exception {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        BookingCommand.BookingRequestCommand command = new BookingCommand.BookingRequestCommand(
                CHECKOUT_TOKEN,
                idempotencyKey,
                List.of(BookingTestFixtures.creditCardMethod())
        );

        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<BookingInfo.BookingResult>> futures = new ArrayList<>();
        AtomicInteger duplicatePaymentCount = new AtomicInteger();

        // act
        for (int i = 0; i < THREAD_COUNT; i++) {
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
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }

        // assert
        assertThat(bookingJpaRepository.count()).isEqualTo(1);
        assertThat(bookingIds).isNotEmpty();
        assertThat(bookingIds).allMatch(id -> id.equals(bookingIds.getFirst()));
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
