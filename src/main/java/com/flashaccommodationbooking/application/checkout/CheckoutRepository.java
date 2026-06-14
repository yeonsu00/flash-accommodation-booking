package com.flashaccommodationbooking.application.checkout;

import java.util.Optional;

public interface CheckoutRepository {

    long reserveStock(Long productId, Long userId, String checkoutToken);

    Optional<CheckoutInfo.TokenInfo> getCheckoutTokenInfo(String checkoutToken);

    void deleteCheckoutToken(String checkoutToken);

}
