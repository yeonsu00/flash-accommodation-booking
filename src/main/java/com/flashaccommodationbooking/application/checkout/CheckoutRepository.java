package com.flashaccommodationbooking.application.checkout;

public interface CheckoutRepository {

    long reserveStock(Long productId, Long userId, String checkoutToken);

}
