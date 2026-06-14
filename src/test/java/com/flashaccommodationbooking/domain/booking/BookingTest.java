package com.flashaccommodationbooking.domain.booking;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.support.BookingTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingTest {

    @DisplayName("Booking.of() 호출 시 status가 PENDING으로 생성된다")
    @Test
    void createsWithPendingStatus() {
        // arrange
        User user = BookingTestFixtures.defaultUser();
        AccommodationProduct product = BookingTestFixtures.defaultProduct();

        // act
        Booking booking = Booking.of(user, product, BookingTestFixtures.PRODUCT_PRICE);

        // assert
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @DisplayName("booking.confirm() 호출 시 status가 CONFIRMED로 변경된다")
    @Test
    void confirmsBooking() {
        // arrange
        User user = BookingTestFixtures.defaultUser();
        AccommodationProduct product = BookingTestFixtures.defaultProduct();
        Booking booking = Booking.of(user, product, BookingTestFixtures.PRODUCT_PRICE);

        // act
        booking.confirm();

        // assert
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }
}
