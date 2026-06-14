package com.flashaccommodationbooking.domain.payment;

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

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(length = 64, unique = true, nullable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private int totalAmount;

    @Builder
    private Payment(Long bookingId, String idempotencyKey, int totalAmount) {
        this.bookingId = bookingId;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = totalAmount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment of(Long bookingId, String idempotencyKey, int totalAmount) {
        return Payment.builder()
                .bookingId(bookingId)
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
