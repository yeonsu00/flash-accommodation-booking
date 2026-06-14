package com.flashaccommodationbooking.infrastructure.checkout;

import com.flashaccommodationbooking.application.checkout.CheckoutInfo;
import com.flashaccommodationbooking.application.checkout.CheckoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final CheckoutRedisRepository checkoutRedisRepository;

    @Override
    public long reserveStock(Long productId, Long userId, String checkoutToken) {
        return checkoutRedisRepository.reserveStock(productId, userId, checkoutToken);
    }

    @Override
    public Optional<CheckoutInfo.TokenInfo> getCheckoutTokenInfo(String checkoutToken) {
        return checkoutRedisRepository.getCheckoutTokenValue(checkoutToken)
                .map(CheckoutInfo.TokenInfo::from);
    }

    @Override
    public void deleteCheckoutToken(String checkoutToken) {
        checkoutRedisRepository.deleteCheckoutToken(checkoutToken);
    }

}
