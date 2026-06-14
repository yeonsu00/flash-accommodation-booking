package com.flashaccommodationbooking.application.booking.event;

public record BookingFailedEvent(String idempotencyKey) {}
