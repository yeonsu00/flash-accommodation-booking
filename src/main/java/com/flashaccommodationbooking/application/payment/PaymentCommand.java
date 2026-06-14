package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.PaymentMethodType;

public class PaymentCommand {

    public record Request(Long bookingId,
                          Long userId,
                          PaymentMethodType methodType,
                          int amount
    ) {
        public static Request of(Long bookingId, Long userId, PaymentMethodType paymentMethodType, int amount) {
            return new Request(bookingId, userId, paymentMethodType, amount);
        }
    }

    public record Method(PaymentMethodType methodType,
                         int amount
    ) {
        public static Method of(String methodType, int amount) {
            return new Method(PaymentMethodType.from(methodType), amount);
        }
    }

}
