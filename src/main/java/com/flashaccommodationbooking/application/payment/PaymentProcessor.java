package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Request;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;

public interface PaymentProcessor {

    PaymentMethodType supports();

    String process(Request command);

    void cancel(Request command);

}
