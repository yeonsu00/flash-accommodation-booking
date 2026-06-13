package com.flashaccommodationbooking.global.common;

import com.flashaccommodationbooking.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class CommonApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    private CommonApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> CommonApiResponse<T> success(T data) {
        return new CommonApiResponse<>("SUCCESS", "요청이 성공했습니다.", data);
    }

    public static <T> CommonApiResponse<T> success() {
        return new CommonApiResponse<>("SUCCESS", "요청이 성공했습니다.", null);
    }

    public static <T> CommonApiResponse<T> fail(ErrorCode errorCode) {
        return new CommonApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> CommonApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new CommonApiResponse<>(errorCode.getCode(), message, null);
    }
}
