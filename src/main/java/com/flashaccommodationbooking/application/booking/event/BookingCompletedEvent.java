package com.flashaccommodationbooking.application.booking.event;

public record BookingCompletedEvent(String checkoutToken, String idempotencyKey, Long bookingId) {}
