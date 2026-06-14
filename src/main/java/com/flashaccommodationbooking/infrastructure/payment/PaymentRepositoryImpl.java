package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentRepository;
import com.flashaccommodationbooking.domain.payment.Payment;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public void savePayment(Payment payment) {
        try {
            paymentJpaRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_PAYMENT);
        }
    }

}
