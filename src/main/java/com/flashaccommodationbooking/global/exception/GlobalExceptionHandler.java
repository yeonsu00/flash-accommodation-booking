package com.flashaccommodationbooking.global.exception;

import com.flashaccommodationbooking.global.common.CommonApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: {}", errorCode.getCode());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(CommonApiResponse.fail(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.BAD_REQUEST.getMessage());
        return ResponseEntity
                .status(400)
                .body(CommonApiResponse.fail(ErrorCode.BAD_REQUEST, message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleMissingRequestHeaderException(
            MissingRequestHeaderException e
    ) {
        return ResponseEntity
                .status(400)
                .body(CommonApiResponse.fail(ErrorCode.BAD_REQUEST, e.getHeaderName() + ": 필수 헤더입니다."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CommonApiResponse<Void>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e
    ) {
        return ResponseEntity
                .status(400)
                .body(CommonApiResponse.fail(ErrorCode.BAD_REQUEST, e.getParameterName() + ": 필수 파라미터입니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonApiResponse<Void>> handleException(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity
                .status(500)
                .body(CommonApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR));
    }
}
