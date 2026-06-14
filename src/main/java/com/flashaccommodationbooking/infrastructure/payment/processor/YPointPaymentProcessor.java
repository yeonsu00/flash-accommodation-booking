package com.flashaccommodationbooking.infrastructure.payment.processor;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Request;
import com.flashaccommodationbooking.application.payment.PaymentProcessor;
import com.flashaccommodationbooking.application.user.UserService;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class YPointPaymentProcessor implements PaymentProcessor {

    private final UserService userService;

    @Override
    public PaymentMethodType supports() {
        return PaymentMethodType.Y_POINT;
    }

    @Override
    public String process(Request command) {
        userService.deductPoint(command.userId(), command.amount());
        return null;
    }

    @Override
    public void cancel(Request command) {
        userService.addPoint(command.userId(), command.amount());
    }

}
