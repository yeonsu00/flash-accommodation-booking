package com.flashaccommodationbooking.interfaces.listener;

import com.flashaccommodationbooking.application.payment.PaymentCompensationEvent;
import com.flashaccommodationbooking.application.payment.PaymentProcessorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentProcessorFactory processorFactory;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handlePaymentCompensation(PaymentCompensationEvent event) {
        event.pgProcessedCommands().forEach(cmd ->
                processorFactory.getProcessor(cmd.methodType()).cancel(cmd)
        );
    }

}
