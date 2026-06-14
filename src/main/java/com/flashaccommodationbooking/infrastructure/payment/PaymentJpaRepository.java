package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.domain.payment.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
}
