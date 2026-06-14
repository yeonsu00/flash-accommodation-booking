package com.flashaccommodationbooking.infrastructure.payment;

import com.flashaccommodationbooking.application.payment.PaymentCommand;
import com.flashaccommodationbooking.application.payment.PaymentClient;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentClient implements PaymentClient {

    private final PgPaymentSimulator pgPaymentSimulator;

    @CircuitBreaker(name = "pg-api")
    @Retry(name = "pg-api", fallbackMethod = "approveFallback")
    @Override
    public String approve(PaymentCommand.Request command) {
        return pgPaymentSimulator.approve(command);
    }

    @CircuitBreaker(name = "pg-api")
    @Retry(name = "pg-api", fallbackMethod = "cancelFallback")
    @Override
    public void cancel(PaymentCommand.Request command) {
        pgPaymentSimulator.cancel(command);
    }

    private String approveFallback(PaymentCommand.Request command, Exception e) {
        BusinessException businessException = findBusinessException(e);
        if (businessException != null) {
            throw businessException;
        }
        log.error("PG 승인 서킷 오픈 또는 재시도 소진 [bookingId: {}, methodType: {}, reason: {}]",
                command.bookingId(), command.methodType(), e.getMessage());
        throw new BusinessException(ErrorCode.PG_SERVICE_UNAVAILABLE);
    }

    private void cancelFallback(PaymentCommand.Request command, Exception e) {
        BusinessException businessException = findBusinessException(e);
        if (businessException != null) {
            throw businessException;
        }
        log.error("PG 취소 서킷 오픈 또는 재시도 소진 [bookingId: {}, methodType: {}, reason: {}]",
                command.bookingId(), command.methodType(), e.getMessage());
        throw new BusinessException(ErrorCode.PG_SERVICE_UNAVAILABLE);
    }

    private BusinessException findBusinessException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
        }
        return null;
    }
}
