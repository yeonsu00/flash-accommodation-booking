package com.flashaccommodationbooking.infrastructure.payment.processor;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Request;
import com.flashaccommodationbooking.application.payment.PaymentProcessor;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class YPayPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentMethodType supports() {
        return PaymentMethodType.Y_PAY;
    }

    @Override
    public String process(Request command) {
        // PG사 연동
        return UUID.randomUUID().toString();
    }

    @Override
    public void cancel(Request command) {
        // PG사 취소
    }

}
