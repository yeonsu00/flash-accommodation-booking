package com.flashaccommodationbooking.interfaces.listener;

import com.flashaccommodationbooking.application.booking.event.BookingCompletedEvent;
import com.flashaccommodationbooking.application.booking.event.BookingFailedEvent;
import com.flashaccommodationbooking.application.booking.BookingService;
import com.flashaccommodationbooking.application.checkout.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final CheckoutService checkoutService;
    private final BookingService bookingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingCompleted(BookingCompletedEvent event) {
        checkoutService.deleteCheckoutToken(event.checkoutToken());
        bookingService.saveIdempotencyResult(event.idempotencyKey(), event.bookingId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleBookingFailed(BookingFailedEvent event) {
        bookingService.deleteIdempotencyKey(event.idempotencyKey());
    }

}
