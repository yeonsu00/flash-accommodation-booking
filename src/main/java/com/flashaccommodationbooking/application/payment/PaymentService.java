package com.flashaccommodationbooking.application.payment;

import com.flashaccommodationbooking.domain.payment.Payment;
import com.flashaccommodationbooking.domain.payment.PaymentMethodDetail;
import com.flashaccommodationbooking.domain.payment.PaymentMethodType;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentMethodDetailRepository paymentMethodDetailRepository;
    private final PaymentProcessorFactory processorFactory;
    private final ApplicationEventPublisher eventPublisher;

    public void processPayments(Long bookingId, String idempotencyKey,
                                List<PaymentCommand.Method> methods, Long userId) {
        int totalAmount = methods.stream().mapToInt(PaymentCommand.Method::amount).sum();
        Payment payment = Payment.of(bookingId, idempotencyKey, totalAmount);
        paymentRepository.savePayment(payment);

        List<PaymentCommand.Request> processedPgCommands = new ArrayList<>();

        for (PaymentCommand.Method method : methods) {
            PaymentCommand.Request cmd = PaymentCommand.Request.of(bookingId, userId, method.methodType(), method.amount());
            try {
                String pgTransactionId = processorFactory.getProcessor(method.methodType()).process(cmd);

                PaymentMethodDetail detail = PaymentMethodDetail.of(payment, method.methodType(), method.amount());
                if (pgTransactionId != null) {
                    detail.assignPgTransactionId(pgTransactionId);
                    processedPgCommands.add(cmd);
                }
                paymentMethodDetailRepository.save(detail);

            } catch (Exception e) {
                log.warn("결제 실패 - PG 승인 취소 예정 [bookingId: {}, methodType: {}, 취소 대상: {}건, reason: {}]",
                        bookingId, method.methodType(), processedPgCommands.size(), e.getMessage());
                eventPublisher.publishEvent(new PaymentCompensationEvent(processedPgCommands));
                throw e;
            }
        }

        payment.complete();
    }

    public void validatePaymentCombination(List<PaymentCommand.Method> methods) {
        boolean hasCreditCard = methods.stream().anyMatch(m -> m.methodType() == PaymentMethodType.CREDIT_CARD);
        boolean hasYPay = methods.stream().anyMatch(m -> m.methodType() == PaymentMethodType.Y_PAY);
        if (hasCreditCard && hasYPay) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_COMBINATION);
        }
    }

}
