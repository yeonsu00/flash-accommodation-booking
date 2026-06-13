package com.flashaccommodationbooking.domain.payment;

import com.flashaccommodationbooking.domain.booking.Booking;
import com.flashaccommodationbooking.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(length = 64, unique = true, nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private int totalAmount;

    @Builder
    private Payment(Booking booking, String idempotencyKey, int totalAmount) {
        this.booking = booking;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = totalAmount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment of(Booking booking, String idempotencyKey, int totalAmount) {
        return Payment.builder()
                .booking(booking)
                .idempotencyKey(idempotencyKey)
                .totalAmount(totalAmount)
                .build();
    }

    public void complete() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}
