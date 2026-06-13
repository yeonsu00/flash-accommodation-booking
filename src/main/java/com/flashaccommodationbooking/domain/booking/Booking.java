package com.flashaccommodationbooking.domain.booking;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.domain.user.User;
import com.flashaccommodationbooking.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "booking")
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accommodation_product_id", nullable = false)
    private AccommodationProduct product;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private int totalAmount;

    @Builder
    private Booking(User user, AccommodationProduct product, int totalAmount) {
        this.user = user;
        this.product = product;
        this.totalAmount = totalAmount;
        this.status = BookingStatus.PENDING;
    }

    public static Booking of(User user, AccommodationProduct product, int totalAmount) {
        return Booking.builder()
                .user(user)
                .product(product)
                .totalAmount(totalAmount)
                .build();
    }

    public void confirm() {
        this.status = BookingStatus.CONFIRMED;
    }

    public void fail() {
        this.status = BookingStatus.FAILED;
    }

    public void cancel() {
        this.status = BookingStatus.CANCELLED;
    }
}
