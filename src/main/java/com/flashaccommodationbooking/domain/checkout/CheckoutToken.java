package com.flashaccommodationbooking.domain.checkout;

import com.flashaccommodationbooking.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "checkout_token")
@RequiredArgsConstructor
public class CheckoutToken extends BaseEntity {

    private static final long TTL_SECONDS = 300L;

    @Id
    private String token;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    @Builder
    private CheckoutToken(String token, Long userId, Long productId, LocalDateTime expiredAt) {
        this.token = token;
        this.userId = userId;
        this.productId = productId;
        this.expiredAt = expiredAt;
    }

    public static CheckoutToken of(String token, Long userId, Long productId) {
        return CheckoutToken.builder()
                .token(token)
                .userId(userId)
                .productId(productId)
                .expiredAt(LocalDateTime.now().plusSeconds(TTL_SECONDS))
                .build();
    }

    public String toRedisValue() {
        return userId + ":" + productId;
    }
}
