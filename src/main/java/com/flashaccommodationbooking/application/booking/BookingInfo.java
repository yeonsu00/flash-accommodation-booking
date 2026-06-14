package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.domain.booking.BookingStatus;

public class BookingInfo {

    public record BookingResult(Long bookingId, BookingStatus status) {

        public static BookingResult of(Long bookingId) {
            return new BookingResult(bookingId, BookingStatus.CONFIRMED);
        }
    }

}
