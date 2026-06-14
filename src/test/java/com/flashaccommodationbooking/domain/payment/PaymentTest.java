package com.flashaccommodationbooking.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    private static final Long BOOKING_ID = 1L;
    private static final String IDEMPOTENCY_KEY = "idempotency-key";
    private static final int TOTAL_AMOUNT = 100_000;

    @DisplayName("Payment.of() 호출 시 status가 PENDING으로 생성된다")
    @Test
    void createsWithPendingStatus() {
        // act
        Payment payment = Payment.of(BOOKING_ID, IDEMPOTENCY_KEY, TOTAL_AMOUNT);

        // assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @DisplayName("payment.complete() 호출 시 status가 SUCCESS로 변경된다")
    @Test
    void completesPayment() {
        // arrange
        Payment payment = Payment.of(BOOKING_ID, IDEMPOTENCY_KEY, TOTAL_AMOUNT);

        // act
        payment.complete();

        // assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @DisplayName("payment.fail() 호출 시 status가 FAILED로 변경된다")
    @Test
    void failsPayment() {
        // arrange
        Payment payment = Payment.of(BOOKING_ID, IDEMPOTENCY_KEY, TOTAL_AMOUNT);

        // act
        payment.fail();

        // assert
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
