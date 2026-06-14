package com.flashaccommodationbooking.domain.payment;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentMethodTypeTest {

    @DisplayName("유효한 문자열이면 정상 변환된다")
    @ParameterizedTest
    @ValueSource(strings = {"CREDIT_CARD", "Y_PAY", "Y_POINT"})
    void convertsValidString(String value) {
        // act
        PaymentMethodType type = PaymentMethodType.from(value);

        // assert
        assertThat(type.name()).isEqualTo(value);
    }

    @DisplayName("유효하지 않은 문자열이면 INVALID_PAYMENT_METHOD 예외가 발생한다")
    @Test
    void throwsException_whenInvalidString() {
        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            PaymentMethodType.from("INVALID_METHOD");
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_METHOD);
    }
}
