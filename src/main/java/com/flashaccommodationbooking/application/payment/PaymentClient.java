package com.flashaccommodationbooking.application.payment;

public interface PaymentClient {

    String approve(PaymentCommand.Request command);

    void cancel(PaymentCommand.Request command);
}
