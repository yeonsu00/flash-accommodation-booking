package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.application.product.ProductService;
import com.flashaccommodationbooking.application.queue.QueueInfo;
import com.flashaccommodationbooking.application.queue.QueueService;
import com.flashaccommodationbooking.application.user.UserService;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckoutFacade {

    private final QueueService queueService;
    private final CheckoutService checkoutService;
    private final ProductService productService;
    private final UserService userService;

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

    @Transactional(readOnly = true)
    public CheckoutInfo.OrderSheet getOrderSheet(String checkoutToken) {
        CheckoutInfo.TokenInfo tokenInfo = checkoutService.getCheckoutTokenInfo(checkoutToken);

        AccommodationProduct product = productService.getProduct(tokenInfo.productId());
        User user = userService.getUser(tokenInfo.userId());

        return new CheckoutInfo.OrderSheet(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getCheckIn(),
                product.getCheckOut(),
                user.getPoint(),
                checkoutToken
        );
    }

}
