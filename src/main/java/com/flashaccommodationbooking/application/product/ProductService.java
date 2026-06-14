package com.flashaccommodationbooking.application.product;

import com.flashaccommodationbooking.application.payment.PaymentCommand.Method;
import com.flashaccommodationbooking.domain.product.AccommodationProduct;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import java.util.List;
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

    public void validateTotalAmount(List<Method> methods, int price) {
        int total = methods.stream().mapToInt(Method::amount).sum();
        if (total != price) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_AMOUNT);
        }
    }

}
