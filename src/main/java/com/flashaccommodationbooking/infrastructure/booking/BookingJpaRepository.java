package com.flashaccommodationbooking.infrastructure.booking;

import com.flashaccommodationbooking.domain.booking.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingJpaRepository extends JpaRepository<Booking, Long> {
}
