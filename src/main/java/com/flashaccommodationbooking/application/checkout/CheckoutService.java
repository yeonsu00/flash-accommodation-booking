package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CheckoutRepository checkoutRepository;

    public void reserveStock(Long productId, Long userId, String checkoutToken) {
        long result = checkoutRepository.reserveStock(productId, userId, checkoutToken);
        if (result == -1L) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }

    public CheckoutInfo.TokenInfo getCheckoutTokenInfo(String checkoutToken) {
        return checkoutRepository.getCheckoutTokenInfo(checkoutToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECKOUT_TOKEN_NOT_FOUND));
    }

    public void deleteCheckoutToken(String checkoutToken) {
        checkoutRepository.deleteCheckoutToken(checkoutToken);
    }

}
