package com.flashaccommodationbooking.infrastructure.checkout;

import com.flashaccommodationbooking.domain.checkout.CheckoutToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CheckoutTokenJpaRepository extends JpaRepository<CheckoutToken, String> {

    Optional<CheckoutToken> findByTokenAndExpiredAtAfter(String token, LocalDateTime now);

    void deleteByToken(String token);
}
