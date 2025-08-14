package com.disaster.alert.alertapi.global.exception;

import com.disaster.alert.alertapi.global.dto.ApiErrorResponse;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
        ErrorCode ec = e.getErrorCode();
        ApiErrorResponse body = ApiErrorResponse.of(ec, request.getRequestURI(), e.getDetail());
        return ResponseEntity.status(ec.getHttpStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e,
            HttpServletRequest request
    ) {
        var fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);

        String field = (fieldError != null) ? fieldError.getField() : null;
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("요청 값 검증 실패");

        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.VALIDATION_FAILED,
                request.getRequestURI(),
                detail,
                field
        );

        return ResponseEntity
                .status(ErrorCode.VALIDATION_FAILED.getHttpStatus())
                .body(body);
    }

    // 알 수 없는 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        ApiErrorResponse body = ApiErrorResponse.of(ec, request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(body);
    }
}
