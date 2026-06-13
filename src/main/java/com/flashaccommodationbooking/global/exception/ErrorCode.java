package com.flashaccommodationbooking.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    BAD_REQUEST(400, "BAD_REQUEST", "잘못된 요청입니다."),
    NOT_FOUND(404, "NOT_FOUND", "리소스를 찾을 수 없습니다."),
    CONFLICT(409, "CONFLICT", "이미 처리된 요청입니다."),
    INTERNAL_SERVER_ERROR(500, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
