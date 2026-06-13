package com.flashaccommodationbooking.domain.payment;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "payment_method_detail")
@EntityListeners(AuditingEntityListener.class)
public class PaymentMethodDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentMethodType methodType;

    @Column(nullable = false)
    private int amount;

    @Column(length = 100)
    private String pgTransactionId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PaymentMethodDetail(Payment payment, PaymentMethodType methodType, int amount) {
        this.payment = payment;
        this.methodType = methodType;
        this.amount = amount;
    }

    public static PaymentMethodDetail of(Payment payment, PaymentMethodType methodType, int amount) {
        return PaymentMethodDetail.builder()
                .payment(payment)
                .methodType(methodType)
                .amount(amount)
                .build();
    }

    public void assignPgTransactionId(String pgTransactionId) {
        this.pgTransactionId = pgTransactionId;
    }
}
