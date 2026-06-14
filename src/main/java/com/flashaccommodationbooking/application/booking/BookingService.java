package com.flashaccommodationbooking.application.booking;

import com.flashaccommodationbooking.domain.booking.Booking;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;

    @Transactional
    public Booking createBooking(User user, AccommodationProduct product) {
        return bookingRepository.save(Booking.of(user, product, product.getPrice()));
    }

    @Transactional
    public void confirm(Booking booking) {
        booking.confirm();
    }

    public boolean setIdempotencyProcessing(String idempotencyKey) {
        return bookingRepository.setIdempotencyProcessing(idempotencyKey);
    }

    public BookingInfo.BookingResult getBookingResult(String idempotencyKey) {
        return bookingRepository.getIdempotencyBookingId(idempotencyKey)
                .map(BookingInfo.BookingResult::of)
                .orElseThrow(() -> new BusinessException(ErrorCode.DUPLICATE_PAYMENT));
    }

    public void saveIdempotencyResult(String idempotencyKey, Long bookingId) {
        bookingRepository.saveIdempotencyResult(idempotencyKey, bookingId);
    }

    public void deleteIdempotencyKey(String idempotencyKey) {
        bookingRepository.deleteIdempotencyKey(idempotencyKey);
    }

}
