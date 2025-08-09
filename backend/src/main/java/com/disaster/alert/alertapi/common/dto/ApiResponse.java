package com.disaster.alert.alertapi.common.dto;

import org.springframework.http.HttpStatus;

public record ApiResponse<T>(
        boolean success,
        String message,
        int code,
        T data

) {
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, HttpStatus.OK.value(), data);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "요청이 성공했습니다.", HttpStatus.OK.value(), data);
    }

    public static <T> ApiResponse<T> empty() {
        return new ApiResponse<>(true, "요청이 성공했습니다.", HttpStatus.OK.value(), null);
    }

    public static <T> ApiResponse<T> failure(String message, HttpStatus status) {
        return new ApiResponse<>(false, message, status.value(), null);
    }
}
