package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.PaymentMethodDetail;

public interface PaymentMethodDetailRepository {

    PaymentMethodDetail save(PaymentMethodDetail detail);

}
