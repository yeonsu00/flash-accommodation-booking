package com.flashaccommodationbooking.interfaces.api.booking;

import com.flashaccommodationbooking.application.booking.BookingCommand;
import com.flashaccommodationbooking.application.booking.BookingInfo;
import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import com.flashaccommodationbooking.domain.booking.BookingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class BookingV1Dto {

    public record BookingRequest(
            @NotBlank String checkoutToken,
            @NotEmpty @Valid List<PaymentMethodRequest> paymentMethods
    ) {
        public BookingCommand.BookingRequestCommand toCommand(String idempotencyKey) {
            return new BookingCommand.BookingRequestCommand(
                    checkoutToken,
                    idempotencyKey,
                    paymentMethods.stream()
                            .map(PaymentMethodRequest::toCommand)
                            .toList()
            );
        }
    }

    public record PaymentMethodRequest(
            @NotBlank String methodType,
            @Positive int amount
    ) {
        public Method toCommand() {
            return Method.of(methodType, amount);
        }
    }

    public record BookingResponse(Long bookingId, BookingStatus status) {

        public static BookingResponse from(BookingInfo.BookingResult result) {
            return new BookingResponse(result.bookingId(), result.status());
        }
    }

}
