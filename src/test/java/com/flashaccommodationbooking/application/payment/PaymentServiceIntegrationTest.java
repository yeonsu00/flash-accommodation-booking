package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.booking.Booking;
import com.flashaccommodationbooking.domain.payment.Payment;
import com.flashaccommodationbooking.domain.payment.PaymentMethodDetail;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.domain.payment.PaymentStatus;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.booking.BookingJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentMethodDetailJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PgPaymentSimulator;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import com.flashaccommodationbooking.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIntegrationTest extends IntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingJpaRepository bookingJpaRepository;

    @Autowired
    private PaymentJpaRepository paymentJpaRepository;

    @Autowired
    private PaymentMethodDetailJpaRepository paymentMethodDetailJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @MockitoSpyBean
    private PgPaymentSimulator pgPaymentSimulator;

    private User user;
    private AccommodationProduct product;
    private Booking booking;

    @BeforeEach
    void setUp() {
        paymentMethodDetailJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();

        user = userJpaRepository.save(BookingTestFixtures.defaultUser());
        product = productJpaRepository.save(BookingTestFixtures.defaultProduct());
        booking = bookingJpaRepository.save(Booking.of(user, product, BookingTestFixtures.PRODUCT_PRICE));

        reset(pgPaymentSimulator);
        doReturn(UUID.randomUUID().toString())
                .when(pgPaymentSimulator)
                .approve(any());
    }

    @AfterEach
    void tearDown() {
        paymentMethodDetailJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }

    @DisplayName("단일 결제 수단(신용카드)으로 결제 시 Payment가 SUCCESS로 저장된다")
    @Test
    @Transactional
    void savesPaymentAsSuccess_whenSingleCreditCardPayment() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        List<PaymentCommand.Method> methods = List.of(BookingTestFixtures.creditCardMethod());

        // act
        paymentService.processPayments(booking.getId(), idempotencyKey, methods, user.getId());

        // assert
        List<Payment> payments = paymentJpaRepository.findAll();
        assertThat(payments).hasSize(1);
        assertThat(payments.getFirst().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @DisplayName("복합 결제(Y포인트 + 신용카드)로 결제 시 두 PaymentMethodDetail이 저장된다")
    @Test
    @Transactional
    void savesTwoPaymentMethodDetails_whenCompositePayment() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        List<PaymentCommand.Method> methods = BookingTestFixtures.compositeYPointAndCreditCard();

        // act
        paymentService.processPayments(booking.getId(), idempotencyKey, methods, user.getId());

        // assert
        List<PaymentMethodDetail> details = paymentMethodDetailJpaRepository.findAll();
        assertThat(details).hasSize(2);
        assertThat(details).extracting(PaymentMethodDetail::getMethodType)
                .containsExactlyInAnyOrder(PaymentMethodType.Y_POINT, PaymentMethodType.CREDIT_CARD);
    }

    @DisplayName("Y포인트 결제 시 pgTransactionId 없이 저장된다")
    @Test
    @Transactional
    void savesWithoutPgTransactionId_whenYPointPayment() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        List<PaymentCommand.Method> methods = List.of(BookingTestFixtures.yPointMethod());

        // act
        paymentService.processPayments(booking.getId(), idempotencyKey, methods, user.getId());

        // assert
        PaymentMethodDetail detail = paymentMethodDetailJpaRepository.findAll().getFirst();
        assertThat(detail.getMethodType()).isEqualTo(PaymentMethodType.Y_POINT);
        assertThat(detail.getPgTransactionId()).isNull();
    }

    @DisplayName("Y포인트 잔액 부족 시 INSUFFICIENT_POINT 예외가 발생한다")
    @Test
    @Transactional
    void throwsException_whenInsufficientPoint() {
        // arrange
        User lowPointUser = userJpaRepository.save(BookingTestFixtures.lowPointUser());
        Booking lowPointBooking = bookingJpaRepository.save(
                Booking.of(lowPointUser, product, BookingTestFixtures.PRODUCT_PRICE)
        );
        String idempotencyKey = UUID.randomUUID().toString();
        List<PaymentCommand.Method> methods = List.of(BookingTestFixtures.yPointMethod());

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayments(lowPointBooking.getId(), idempotencyKey, methods, lowPointUser.getId());
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_POINT);
    }

    @DisplayName("동일 idempotencyKey로 Payment 저장 재시도 시 DUPLICATE_PAYMENT 예외가 발생한다")
    @Test
    void throwsDuplicatePayment_whenSameIdempotencyKeySavedTwice() {
        // arrange
        String idempotencyKey = UUID.randomUUID().toString();
        List<PaymentCommand.Method> methods = List.of(BookingTestFixtures.creditCardMethod());
        paymentService.processPayments(booking.getId(), idempotencyKey, methods, user.getId());

        Booking duplicateBooking = bookingJpaRepository.save(
                Booking.of(user, product, BookingTestFixtures.PRODUCT_PRICE)
        );

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () ->
                paymentService.processPayments(duplicateBooking.getId(), idempotencyKey, methods, user.getId())
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_PAYMENT);
        assertThat(paymentJpaRepository.count()).isEqualTo(1);
    }
}
