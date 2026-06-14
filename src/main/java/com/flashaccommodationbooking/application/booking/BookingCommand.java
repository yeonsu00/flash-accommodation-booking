package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import java.util.List;

public class BookingCommand {

    public record BookingRequestCommand(
            String checkoutToken,
            String idempotencyKey,
            List<Method> paymentMethods
    ) {}

}
