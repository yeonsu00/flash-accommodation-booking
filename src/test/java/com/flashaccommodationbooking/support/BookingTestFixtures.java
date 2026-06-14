package com.flashaccommodationbooking.support;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;

import java.time.LocalDateTime;
import java.util.List;

public final class BookingTestFixtures {

    public static final int PRODUCT_PRICE = 100_000;
    public static final LocalDateTime CHECK_IN = LocalDateTime.of(2026, 7, 1, 15, 0);
    public static final LocalDateTime CHECK_OUT = LocalDateTime.of(2026, 7, 2, 11, 0);

    private BookingTestFixtures() {
    }

    public static User defaultUser() {
        return User.of("test-user", 150_000);
    }

    public static User lowPointUser() {
        return User.of("low-point-user", 10_000);
    }

    public static AccommodationProduct defaultProduct() {
        return AccommodationProduct.of(
                "숙소 이름",
                PRODUCT_PRICE,
                CHECK_IN,
                CHECK_OUT,
                LocalDateTime.now().minusHours(1),
                10
        );
    }

    public static Method creditCardMethod() {
        return new Method(PaymentMethodType.CREDIT_CARD, PRODUCT_PRICE);
    }

    public static Method yPointMethod() {
        return new Method(PaymentMethodType.Y_POINT, PRODUCT_PRICE);
    }

    public static Method yPayMethod() {
        return new Method(PaymentMethodType.Y_PAY, PRODUCT_PRICE);
    }

    public static List<Method> compositeYPointAndCreditCard() {
        return List.of(
                new Method(PaymentMethodType.Y_POINT, 30_000),
                new Method(PaymentMethodType.CREDIT_CARD, 70_000)
        );
    }

    public static List<Method> invalidCreditCardAndYPay() {
        return List.of(
                new Method(PaymentMethodType.CREDIT_CARD, 50_000),
                new Method(PaymentMethodType.Y_PAY, 50_000)
        );
    }

    public static List<Method> compositeYPointAndYPay() {
        return List.of(
                new Method(PaymentMethodType.Y_POINT, 40_000),
                new Method(PaymentMethodType.Y_PAY, 60_000)
        );
    }
}
