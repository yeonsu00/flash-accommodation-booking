package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMethodDetailRepository paymentMethodDetailRepository;

    @Mock
    private PaymentProcessorFactory processorFactory;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    @DisplayName("신용카드 + Y페이 혼용 시 INVALID_PAYMENT_COMBINATION 예외가 발생한다")
    @Test
    void throwsException_whenCreditCardAndYPayCombined() {
        // act & assert
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.validatePaymentCombination(BookingTestFixtures.invalidCreditCardAndYPay());
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_PAYMENT_COMBINATION);
    }

    @DisplayName("Y포인트 + 신용카드 조합은 통과한다")
    @Test
    void passes_whenYPointAndCreditCardCombined() {
        // act & assert
        assertDoesNotThrow(() -> paymentService.validatePaymentCombination(BookingTestFixtures.compositeYPointAndCreditCard()));
    }

    @DisplayName("Y포인트 + Y페이 조합은 통과한다")
    @Test
    void passes_whenYPointAndYPayCombined() {
        // act & assert
        assertDoesNotThrow(() -> paymentService.validatePaymentCombination(BookingTestFixtures.compositeYPointAndYPay()));
    }
}
