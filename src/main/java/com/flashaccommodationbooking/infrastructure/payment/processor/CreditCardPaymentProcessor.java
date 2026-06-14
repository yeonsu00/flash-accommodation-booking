package com.flashaccommodationbooking.infrastructure.payment.processor;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Request;
import com.flashaccommodationbooking.application.payment.PaymentProcessor;
import com.flashaccommodationbooking.application.payment.PaymentClient;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreditCardPaymentProcessor implements PaymentProcessor {

    private final PaymentClient paymentClient;

    @Override
    public PaymentMethodType supports() {
        return PaymentMethodType.CREDIT_CARD;
    }

    @Override
    public String process(Request command) {
        return paymentClient.approve(command);
    }

    @Override
    public void cancel(Request command) {
        paymentClient.cancel(command);
    }
}
