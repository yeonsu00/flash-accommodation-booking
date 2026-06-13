package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutFacade {

    private final QueueService queueService;
    private final CheckoutService checkoutService;

    public String reserveStock(String queueToken) {
        QueueInfo.TokenInfo tokenInfo = queueService.getTokenInfo(queueToken);

        if (!queueService.isAdmitted(tokenInfo.productId(), queueToken)) {
            throw new BusinessException(ErrorCode.CHECKOUT_NOT_ADMITTED);
        }

        String checkoutToken = UUID.randomUUID().toString();
        checkoutService.reserveStock(tokenInfo.productId(), tokenInfo.userId(), checkoutToken);

        queueService.removeFromAdmitted(tokenInfo.productId(), queueToken);

        return checkoutToken;
    }

}
