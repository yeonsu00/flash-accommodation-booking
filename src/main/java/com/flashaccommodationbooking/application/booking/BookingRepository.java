package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.domain.booking.Booking;

import java.util.Optional;

public interface BookingRepository {

    Booking save(Booking booking);

    boolean setIdempotencyProcessing(String key);

    Optional<Long> getIdempotencyBookingId(String key);

    void saveIdempotencyResult(String key, Long bookingId);

    void deleteIdempotencyKey(String key);

}
