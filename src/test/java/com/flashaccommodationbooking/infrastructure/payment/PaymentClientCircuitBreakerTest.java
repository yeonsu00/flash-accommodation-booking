package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentCommand;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import com.flashaccommodationbooking.global.exception.PgNetworkException;
import com.flashaccommodationbooking.support.IntegrationTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PgPaymentClient CircuitBreaker/Retry 통합 테스트")
class PaymentClientCircuitBreakerTest extends IntegrationTest {

    private static final PaymentCommand.Request REQUEST = PaymentCommand.Request.of(
            1L, 1L, PaymentMethodType.CREDIT_CARD, 100_000
    );

    @Autowired
    private PgPaymentClient pgPaymentClient;

    @MockitoSpyBean
    private PgPaymentSimulator pgPaymentSimulator;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-api");
        circuitBreaker.transitionToClosedState();
        circuitBreaker.reset();
        reset(pgPaymentSimulator);
    }

    @AfterEach
    void tearDown() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-api");
        circuitBreaker.transitionToClosedState();
        circuitBreaker.reset();
    }

    @DisplayName("BusinessException 발생 시 재시도 없이 즉시 전파되고 approve()는 1회만 호출된다")
    @Order(1)
    @Test
    void doesNotRetry_whenBusinessExceptionOccurs() {
        // arrange
        assertThat(circuitBreakerRegistry.circuitBreaker("pg-api").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        doThrow(new BusinessException(ErrorCode.PAYMENT_FAILED))
                .when(pgPaymentSimulator)
                .approve(any());

        // act
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            pgPaymentClient.approve(REQUEST);
        });

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        verify(pgPaymentSimulator, times(1)).approve(any());
    }

    @DisplayName("PgNetworkException 발생 시 approve()가 최대 3회 재시도된다")
    @Order(2)
    @Test
    void retriesUpToMaxAttempts_whenPgNetworkExceptionOccurs() {
        // arrange
        doThrow(new PgNetworkException("PG 승인 실패: 네트워크 오류"))
                .when(pgPaymentSimulator)
                .approve(any());

        // act
        assertThrows(BusinessException.class, () -> pgPaymentClient.approve(REQUEST));

        // assert
        verify(pgPaymentSimulator, times(3)).approve(any());
    }

    @DisplayName("재시도 3회 소진 후 PG_SERVICE_UNAVAILABLE BusinessException이 발생한다")
    @Order(3)
    @Test
    void throwsBusinessException_whenAllRetriesExhausted() {
        // arrange
        doThrow(new PgNetworkException("PG 승인 실패: 네트워크 오류"))
                .when(pgPaymentSimulator)
                .approve(any());

        // act
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            pgPaymentClient.approve(REQUEST);
        });

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PG_SERVICE_UNAVAILABLE);
        verify(pgPaymentSimulator, times(3)).approve(any());
    }

    @DisplayName("실패율이 임계치를 넘으면 서킷이 OPEN되고 이후 PG 호출 없이 즉시 실패한다")
    @Order(4)
    @Test
    void opensCircuit_whenFailureRateExceedsThreshold() {
        // arrange
        doThrow(new PgNetworkException("PG 승인 실패: 네트워크 오류"))
                .when(pgPaymentSimulator)
                .approve(any());

        for (int i = 0; i < 10; i++) {
            assertThrows(BusinessException.class, () -> pgPaymentClient.approve(REQUEST));
        }

        int invocationCount = org.mockito.Mockito.mockingDetails(pgPaymentSimulator).getInvocations().size();

        // act
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            pgPaymentClient.approve(REQUEST);
        });

        // assert
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PG_SERVICE_UNAVAILABLE);
        assertThat(circuitBreakerRegistry.circuitBreaker("pg-api").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(org.mockito.Mockito.mockingDetails(pgPaymentSimulator).getInvocations()).hasSize(invocationCount);
    }

    @DisplayName("서킷 OPEN 후 wait-duration 경과 시 HALF_OPEN을 거쳐 CLOSED로 복구된다")
    @Order(5)
    @Test
    void recoverAfterWaitDuration_whenCircuitIsOpen() throws InterruptedException {
        // arrange
        doThrow(new PgNetworkException("PG 승인 실패: 네트워크 오류"))
                .when(pgPaymentSimulator)
                .approve(any());

        for (int i = 0; i < 10; i++) {
            assertThrows(BusinessException.class, () -> pgPaymentClient.approve(REQUEST));
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("pg-api");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        Thread.sleep(300);

        // act - wait-duration 경과 후 호출 시 HALF_OPEN을 거쳐 복구된다
        doReturn("recovered-txn-id")
                .when(pgPaymentSimulator)
                .approve(any());

        String result = pgPaymentClient.approve(REQUEST);

        // assert
        assertThat(result).isEqualTo("recovered-txn-id");
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
