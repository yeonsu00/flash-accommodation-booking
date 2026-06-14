package com.flashaccommodationbooking.application.product;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @DisplayName("결제 수단 합계와 상품 가격이 일치하면 통과한다")
    @Test
    void passes_whenTotalAmountMatchesPrice() {
        // arrange
        List<Method> methods = List.of(
                new Method(PaymentMethodType.Y_POINT, 30_000),
                new Method(PaymentMethodType.CREDIT_CARD, 70_000)
        );

        // act & assert
        assertDoesNotThrow(() -> productService.validateTotalAmount(methods, 100_000));
    }

    @DisplayName("합계가 불일치하면 INVALID_PAYMENT_AMOUNT 예외가 발생한다")
    @Test
    void throwsException_whenTotalAmountMismatch() {
        // arrange
        List<Method> methods = List.of(
                new Method(PaymentMethodType.CREDIT_CARD, 50_000)
        );

        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            productService.validateTotalAmount(methods, 100_000);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_AMOUNT);
    }
}
