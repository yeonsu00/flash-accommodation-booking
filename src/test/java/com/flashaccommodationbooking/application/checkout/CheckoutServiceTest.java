package com.flashaccommodationbooking.application.checkout;

import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private CheckoutRepository checkoutRepository;

    @InjectMocks
    private CheckoutService checkoutService;

    private static final Long PRODUCT_ID = 100L;
    private static final Long USER_ID = 1L;
    private static final String CHECKOUT_TOKEN = "checkout-token-uuid";

    @DisplayName("reserveStock()")
    @Nested
    class ReserveStock {

        @DisplayName("Lua 결과가 1이면, 예외 없이 재고 선점에 성공한다.")
        @Test
        void reservesStock_whenLuaReturnsOne() {
            // arrange
            when(checkoutRepository.reserveStock(PRODUCT_ID, USER_ID, CHECKOUT_TOKEN)).thenReturn(1L);

            // act & assert
            assertDoesNotThrow(() -> checkoutService.reserveStock(PRODUCT_ID, USER_ID, CHECKOUT_TOKEN));
            verify(checkoutRepository).reserveStock(PRODUCT_ID, USER_ID, CHECKOUT_TOKEN);
        }

        @DisplayName("Lua 결과가 -1이면, PRODUCT_OUT_OF_STOCK 예외가 발생한다.")
        @Test
        void throwsException_whenLuaReturnsMinusOne() {
            // arrange
            when(checkoutRepository.reserveStock(PRODUCT_ID, USER_ID, CHECKOUT_TOKEN)).thenReturn(-1L);

            // act & assert
            BusinessException exception = assertThrows(BusinessException.class, () -> {
                checkoutService.reserveStock(PRODUCT_ID, USER_ID, CHECKOUT_TOKEN);
            });

            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
    }
}
