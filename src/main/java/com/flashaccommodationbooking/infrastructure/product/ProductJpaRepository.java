package com.flashaccommodationbooking.infrastructure.product;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<AccommodationProduct, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM AccommodationProduct p WHERE p.id = :id")
    Optional<AccommodationProduct> findByIdWithPessimisticLock(@Param("id") Long id);
}
