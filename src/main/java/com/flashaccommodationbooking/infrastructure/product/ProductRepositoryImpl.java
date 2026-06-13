package com.flashaccommodationbooking.infrastructure.product;

import com.flashaccommodationbooking.application.product.ProductRepository;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    @Override
    public AccommodationProduct getById(Long id) {
        return productJpaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
    }

}
