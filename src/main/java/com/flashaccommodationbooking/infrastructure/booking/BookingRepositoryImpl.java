package com.flashaccommodationbooking.infrastructure.booking;

import com.flashaccommodationbooking.application.booking.BookingRepository;
import com.flashaccommodationbooking.domain.booking.Booking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class BookingRepositoryImpl implements BookingRepository {

    private final BookingJpaRepository bookingJpaRepository;
    private final IdempotencyRedisRepository idempotencyRedisRepository;

    @Override
    public Booking save(Booking booking) {
        return bookingJpaRepository.save(booking);
    }

    @Override
    public boolean setIdempotencyProcessing(String key) {
        return idempotencyRedisRepository.setProcessing(key);
    }

    @Override
    public Optional<Long> getIdempotencyBookingId(String key) {
        return idempotencyRedisRepository.getBookingId(key);
    }

    @Override
    public void saveIdempotencyResult(String key, Long bookingId) {
        idempotencyRedisRepository.saveResult(key, bookingId);
    }

    @Override
    public void deleteIdempotencyKey(String key) {
        idempotencyRedisRepository.delete(key);
    }

}
