package com.flashaccommodationbooking.domain.product;

import com.flashaccommodationbooking.global.entity.BaseEntity;
import com.flashaccommodationbooking.global.exception.BusinessException;
import com.flashaccommodationbooking.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
@Entity
@Table(name = "accommodation_product")
public class AccommodationProduct extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private LocalDateTime checkIn;

    @Column(nullable = false)
    private LocalDateTime checkOut;

    @Column(nullable = false)
    private LocalDateTime openAt;

    @Column(nullable = false)
    private int stock;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ProductStatus status;

    @Builder
    private AccommodationProduct(String name, int price, LocalDateTime checkIn, LocalDateTime checkOut,
                                  LocalDateTime openAt, int stock) {
        this.name = name;
        this.price = price;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.openAt = openAt;
        this.stock = stock;
        this.status = ProductStatus.OPEN;
    }

    public static AccommodationProduct of(String name, int price, LocalDateTime checkIn, LocalDateTime checkOut,
                                           LocalDateTime openAt, int stock) {
        return AccommodationProduct.builder()
                .name(name)
                .price(price)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .openAt(openAt)
                .stock(stock)
                .build();
    }

    public void decreaseStock() {
        if (this.stock <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_OUT_OF_STOCK);
        }
        this.stock--;
        if (this.stock == 0) {
            this.status = ProductStatus.SOLD_OUT;
        }
    }

    public void increaseStock() {
        this.stock++;
        if (this.status == ProductStatus.SOLD_OUT) {
            this.status = ProductStatus.OPEN;
        }
    }
}
