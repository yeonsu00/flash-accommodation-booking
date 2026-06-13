package com.flashaccommodationbooking.application.product;

import com.flashaccommodationbooking.domain.product.AccommodationProduct;

public interface ProductRepository {

    AccommodationProduct getById(Long id);

}
