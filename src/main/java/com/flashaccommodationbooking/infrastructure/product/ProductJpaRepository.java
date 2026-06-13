package com.flashaccommodationbooking.infrastructure.product;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<AccommodationProduct, Long> {
}
