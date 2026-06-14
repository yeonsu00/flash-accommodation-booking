package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.Payment;

public interface PaymentRepository {

    void savePayment(Payment payment);

}
