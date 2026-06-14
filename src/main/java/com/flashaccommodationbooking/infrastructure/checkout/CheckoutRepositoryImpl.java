package com.flashaccommodationbooking.infrastructure.checkout;

import com.flashaccommodationbooking.application.checkout.CheckoutInfo;
import com.flashaccommodationbooking.application.checkout.CheckoutRepository;
import com.flashaccommodationbooking.domain.checkout.CheckoutToken;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.infrastructure.product.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CheckoutRepositoryImpl implements CheckoutRepository {

    private final CheckoutRedisRepository checkoutRedisRepository;
    private final CheckoutTokenJpaRepository checkoutTokenJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    @Transactional
    @Override
    public long reserveStock(Long productId, Long userId, String checkoutToken) {
        long result = checkoutRedisRepository.reserveStock(productId, userId, checkoutToken);
        if (result == 1L) {
            checkoutTokenJpaRepository.save(CheckoutToken.of(checkoutToken, userId, productId));
            return 1L;
        }
        if (result == -2L) {
            log.warn("Redis 장애 - DB FOR UPDATE로 재고 선점 [productId: {}]", productId);
            return reserveStockWithLock(productId, userId, checkoutToken);
        }
        return result; // -1L: 재고 없음
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CheckoutInfo.TokenInfo> getCheckoutTokenInfo(String checkoutToken) {
        return checkoutRedisRepository.getCheckoutTokenValue(checkoutToken)
                .or(() -> checkoutTokenJpaRepository
                        .findByTokenAndExpiredAtAfter(checkoutToken, LocalDateTime.now())
                        .map(CheckoutToken::toRedisValue))
                .map(CheckoutInfo.TokenInfo::from);
    }

    @Transactional
    @Override
    public void deleteCheckoutToken(String checkoutToken) {
        checkoutRedisRepository.deleteCheckoutToken(checkoutToken);
        checkoutTokenJpaRepository.deleteByToken(checkoutToken);
    }

    private long reserveStockWithLock(Long productId, Long userId, String checkoutToken) {
        AccommodationProduct product = productJpaRepository.findByIdWithPessimisticLock(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.decreaseStock();
        checkoutTokenJpaRepository.save(CheckoutToken.of(checkoutToken, userId, productId));
        return 1L;
    }
}
