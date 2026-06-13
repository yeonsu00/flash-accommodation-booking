package com.flashaccommodationbooking.application.product;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public AccommodationProduct getProduct(Long productId) {
        return productRepository.getById(productId);
    }

}
