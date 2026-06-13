package com.flashaccommodationbooking.infrastructure.checkout;

import com.flashaccommodationbooking.application.checkout.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final CheckoutRedisRepository checkoutRedisRepository;

    @Override
    public long reserveStock(Long productId, Long userId, String checkoutToken) {
        return checkoutRedisRepository.reserveStock(productId, userId, checkoutToken);
    }

}
