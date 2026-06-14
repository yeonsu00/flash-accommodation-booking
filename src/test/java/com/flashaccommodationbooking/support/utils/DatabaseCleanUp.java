package com.flashaccommodationbooking.support.utils;

import com.flashaccommodationbooking.infrastructure.booking.BookingJpaRepository;
import com.flashaccommodationbooking.infrastructure.checkout.CheckoutTokenJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentJpaRepository;
import com.flashaccommodationbooking.infrastructure.payment.PaymentMethodDetailJpaRepository;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import com.flashaccommodationbooking.infrastructure.user.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseCleanUp {

    private final PaymentMethodDetailJpaRepository paymentMethodDetailJpaRepository;
    private final PaymentJpaRepository paymentJpaRepository;
    private final BookingJpaRepository bookingJpaRepository;
    private final CheckoutTokenJpaRepository checkoutTokenJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    public void truncateAll() {
        paymentMethodDetailJpaRepository.deleteAll();
        paymentJpaRepository.deleteAll();
        bookingJpaRepository.deleteAll();
        checkoutTokenJpaRepository.deleteAll();
        userJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
    }
}
