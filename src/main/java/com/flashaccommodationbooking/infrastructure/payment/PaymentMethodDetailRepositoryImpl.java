package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentMethodDetailRepository;
import com.flashaccommodationbooking.domain.payment.PaymentMethodDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentMethodDetailRepositoryImpl implements PaymentMethodDetailRepository {

    private final PaymentMethodDetailJpaRepository paymentMethodDetailJpaRepository;

    @Override
    public PaymentMethodDetail save(PaymentMethodDetail detail) {
        return paymentMethodDetailJpaRepository.save(detail);
    }

}
