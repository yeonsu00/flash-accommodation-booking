package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentCommand;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.PgNetworkException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PgPaymentSimulator 단위 테스트")
class PgPaymentClientTest {

    private static final PaymentCommand.Request REQUEST = PaymentCommand.Request.of(
            1L, 1L, PaymentMethodType.CREDIT_CARD, 100_000
    );

    @DisplayName("PG 승인 성공 시 UUID 형식의 pgTransactionId를 반환한다")
    @Test
    void returnsTransactionId_whenApproveSucceeds() {
        // arrange
        PgPaymentSimulator simulator = new FixedRollPgPaymentSimulator(0);

        // act
        String pgTransactionId = simulator.approve(REQUEST);

        // assert
        assertThat(pgTransactionId)
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @DisplayName("PG 승인 네트워크 오류 시 PgNetworkException이 발생한다")
    @Test
    void throwsPgNetworkException_whenApproveFailsWithNetworkError() {
        // arrange
        PgPaymentSimulator simulator = new FixedRollPgPaymentSimulator(6);

        // act & assert
        PgNetworkException exception = assertThrows(PgNetworkException.class, () -> {
            simulator.approve(REQUEST);
        });

        assertThat(exception.getMessage()).contains("네트워크 오류");
    }

    @DisplayName("PG 승인 타임아웃 시 PgNetworkException이 발생한다")
    @Test
    void throwsPgNetworkException_whenApproveFailsWithTimeout() {
        // arrange
        PgPaymentSimulator simulator = new FixedRollPgPaymentSimulator(9);

        // act & assert
        PgNetworkException exception = assertThrows(PgNetworkException.class, () -> {
            simulator.approve(REQUEST);
        });

        assertThat(exception.getMessage()).contains("타임아웃");
    }

    @DisplayName("PG 취소 성공 시 예외 없이 완료된다")
    @Test
    void completesWithoutException_whenCancelSucceeds() {
        // arrange
        PgPaymentSimulator simulator = new FixedRollPgPaymentSimulator(3);

        // act & assert
        assertDoesNotThrow(() -> simulator.cancel(REQUEST));
    }

    @DisplayName("PG 취소 실패 시 PgNetworkException이 발생한다")
    @Test
    void throwsPgNetworkException_whenCancelFails() {
        // arrange
        PgPaymentSimulator simulator = new FixedRollPgPaymentSimulator(7);

        // act & assert
        assertThrows(PgNetworkException.class, () -> simulator.cancel(REQUEST));
    }

    private static class FixedRollPgPaymentSimulator extends PgPaymentSimulator {

        private final int roll;

        private FixedRollPgPaymentSimulator(int roll) {
            this.roll = roll;
        }

        @Override
        int nextRoll() {
            return roll;
        }
    }
}
