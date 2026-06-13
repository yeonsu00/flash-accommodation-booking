package com.flashaccommodationbooking.interfaces.api.checkout;

import com.flashaccommodationbooking.application.checkout.CheckoutInfo;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

public class CheckoutV1Dto {

    public record CheckoutRequest(@NotBlank String queueToken) {}

    public record CheckoutResponse(String checkoutToken) {}

    public record ProductInfo(Long id,
                              String name,
                              int price,
                              LocalDateTime checkIn,
                              LocalDateTime checkOut
    ) {
    }

    public record OrderSheetResponse(ProductInfo product, int point, String checkoutToken) {

        public static OrderSheetResponse from(CheckoutInfo.OrderSheet orderSheet) {
            return new OrderSheetResponse(
                    new ProductInfo(
                            orderSheet.productId(),
                            orderSheet.productName(),
                            orderSheet.price(),
                            orderSheet.checkIn(),
                            orderSheet.checkOut()
                    ),
                    orderSheet.point(),
                    orderSheet.checkoutToken()
            );
        }
    }

}
