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

    // Payment
    INSUFFICIENT_POINT(400, "INSUFFICIENT_POINT", "포인트가 부족합니다.");

    private final int status;
    private final String code;
    private final String message;
}
