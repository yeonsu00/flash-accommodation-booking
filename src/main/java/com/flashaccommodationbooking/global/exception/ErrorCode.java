package com.flashaccommodationbooking.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),

    // Product
    PRODUCT_NOT_FOUND(404, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_AVAILABLE(404, "PRODUCT_NOT_AVAILABLE", "예약 가능한 상태의 상품을 찾을 수 없습니다."),
    PRODUCT_OUT_OF_STOCK(409, "PRODUCT_OUT_OF_STOCK", "재고가 소진되었습니다."),

    // Queue
    QUEUE_NOT_OPEN(400, "QUEUE_NOT_OPEN", "아직 대기열 등록 시간이 아닙니다."),
    QUEUE_TOKEN_NOT_FOUND(404, "QUEUE_TOKEN_NOT_FOUND", "유효하지 않거나 만료된 대기열 토큰입니다."),

    // Checkout
    CHECKOUT_NOT_ADMITTED(403, "CHECKOUT_NOT_ADMITTED", "입장 허가된 사용자가 아닙니다."),
    CHECKOUT_TOKEN_NOT_FOUND(404, "CHECKOUT_TOKEN_NOT_FOUND", "유효하지 않거나 만료된 주문 토큰입니다."),

    // Booking
    DUPLICATE_PAYMENT(409, "DUPLICATE_PAYMENT", "이미 처리 중인 결제 요청입니다."),
    INVALID_PAYMENT_COMBINATION(400, "INVALID_PAYMENT_COMBINATION", "신용카드와 Y페이는 함께 사용할 수 없습니다."),
    INVALID_PAYMENT_AMOUNT(400, "INVALID_PAYMENT_AMOUNT", "결제 금액의 합계가 상품 가격과 일치하지 않습니다."),
    PAYMENT_FAILED(400, "PAYMENT_FAILED", "결제에 실패했습니다."),

    // User
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    // Payment
    INSUFFICIENT_POINT(400, "INSUFFICIENT_POINT", "포인트가 부족합니다."),
    INVALID_PAYMENT_METHOD(400, "INVALID_PAYMENT_METHOD", "유효하지 않은 결제 수단입니다.");

    private final int status;
    private final String code;
    private final String message;
}
