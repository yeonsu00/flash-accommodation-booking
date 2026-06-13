package com.flashaccommodationbooking.application.checkout;

import java.time.LocalDateTime;

public class CheckoutInfo {

    public record TokenInfo(Long userId, Long productId) {

        public static TokenInfo from(String value) {
            String[] parts = value.split(":");
            return new TokenInfo(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
        }
    }

    public record OrderSheet(
            Long productId,
            String productName,
            int price,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            int point,
            String checkoutToken
    ) {}

}
