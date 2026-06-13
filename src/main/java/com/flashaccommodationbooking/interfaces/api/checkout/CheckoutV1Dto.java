package com.flashaccommodationbooking.interfaces.api.checkout;

import jakarta.validation.constraints.NotBlank;

public class CheckoutV1Dto {

    public record CheckoutRequest(@NotBlank String queueToken) {}

    public record CheckoutResponse(String checkoutToken) {}

}
