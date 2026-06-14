package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PaymentProcessorFactory {

    private final List<PaymentProcessor> processors;

    private Map<PaymentMethodType, PaymentProcessor> processorMap() {
        return processors.stream()
                .collect(Collectors.toMap(PaymentProcessor::supports, Function.identity()));
    }

    public PaymentProcessor getProcessor(PaymentMethodType methodType) {
        PaymentProcessor processor = processorMap().get(methodType);
        if (processor == null) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED);
        }
        return processor;
    }

}
