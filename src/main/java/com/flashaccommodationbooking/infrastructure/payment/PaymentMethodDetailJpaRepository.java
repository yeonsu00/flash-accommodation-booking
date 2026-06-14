package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.domain.payment.PaymentMethodDetail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMethodDetailJpaRepository extends JpaRepository<PaymentMethodDetail, Long> {
}
