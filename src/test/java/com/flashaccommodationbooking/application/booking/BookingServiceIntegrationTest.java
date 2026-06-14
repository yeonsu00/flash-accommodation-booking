package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.domain.booking.Booking;
import com.flashaccommodationbooking.domain.booking.BookingStatus;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.booking.BookingJpaRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private User user;
    private AccommodationProduct product;

    @BeforeEach
    void setUp() {
        redisCleanUp.truncateAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();

        user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        bookingJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @DisplayName("createBooking() 호출 시 Booking이 저장된다")
    @Test
    void savesBooking_whenCreateBookingCalled() {
        // act
        Booking booking = bookingService.createBooking(user, product);

        // assert
        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(bookingJpaRepository.findById(booking.getId())).isPresent();
    }

    @DisplayName("confirm() 호출 시 Booking status가 CONFIRMED로 변경된다")
    @Test
    void confirmsBooking_whenConfirmCalled() {
        // arrange
        Booking booking = bookingService.createBooking(user, product);

        // act
        bookingService.confirm(booking);

        // assert
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @DisplayName("setIdempotencyProcessing() 첫 호출 시 true를 반환한다")
    @Test
    void returnsTrue_whenFirstIdempotencyProcessingCall() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();

        // act
        boolean result = bookingService.setIdempotencyProcessing(idempotencyKey);

        // assert
        assertTrue(result);
    }

    @DisplayName("setIdempotencyProcessing() 동일 키 재호출 시 false를 반환한다")
    @Test
    void returnsFalse_whenSameIdempotencyKeyCalledAgain() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        bookingService.setIdempotencyProcessing(idempotencyKey);

        // act
        boolean result = bookingService.setIdempotencyProcessing(idempotencyKey);

        // assert
        assertThat(result).isFalse();
    }

    @DisplayName("getBookingResult() 결과가 저장된 키이면 bookingId를 반환한다")
    @Test
    void returnsBookingId_whenIdempotencyResultExists() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        bookingService.setIdempotencyProcessing(idempotencyKey);
        bookingService.saveIdempotencyResult(idempotencyKey, 42L);

        // act
        BookingInfo.BookingResult result = bookingService.getBookingResult(idempotencyKey);

        // assert
        assertThat(result.bookingId()).isEqualTo(42L);
    }

    @DisplayName("getBookingResult() 결과가 없는 키이면 DUPLICATE_PAYMENT 예외가 발생한다")
    @Test
    void throwsException_whenIdempotencyStillProcessing() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        bookingService.setIdempotencyProcessing(idempotencyKey);

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            bookingService.getBookingResult(idempotencyKey);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_PAYMENT);
    }
}
