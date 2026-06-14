package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.Payment;

public interface PaymentRepository {

    Payment save(Payment payment);

}
