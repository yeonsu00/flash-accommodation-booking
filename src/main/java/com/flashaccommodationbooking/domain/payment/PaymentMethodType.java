package com.flashaccommodationbooking.domain.payment;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;

import java.util.Arrays;

public enum PaymentMethodType {
    CREDIT_CARD, Y_PAY, Y_POINT;

    public static PaymentMethodType from(String value) {
        return Arrays.stream(values())
                .filter(t -> t.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PAYMENT_METHOD));
    }
}
