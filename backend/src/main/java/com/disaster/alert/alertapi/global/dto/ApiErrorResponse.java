package com.disaster.alert.alertapi.global.dto;

import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import lombok.Getter;

import java.time.OffsetDateTime;


@Getter
public class ApiErrorResponse {
    private final String code;              // 업무용 에러 코드 (예: C001, M404)
    private final String key;
    private final String message;           // 사용자(클라이언트)용 메시지
    private final int status;               // HTTP 상태 코드
    private final String path;              // 요청 경로
    private final OffsetDateTime timestamp; // ISO-8601
    private final String field;              // 필드명 (선택적, 특정 필드에 대한 에러 메시지)

    private ApiErrorResponse(String code, String key, String message, int status, String path, String field) {
        this.code = code;
        this.key = key;
        this.message = message;
        this.status = status;
        this.path = path;
        this.timestamp = OffsetDateTime.now();
        this.field = field;
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String path) {
        return new ApiErrorResponse(errorCode.getCode(), errorCode.name(), errorCode.getMessage(), errorCode.getHttpStatus(), path, null);
    }

    public static ApiErrorResponse of(ErrorCode errorCode, String path, String detail) {
        String msg = detail == null ? errorCode.getMessage() : errorCode.getMessage() + " - " + detail;
        return new ApiErrorResponse(errorCode.getCode(), errorCode.name(), msg, errorCode.getHttpStatus(), path, null);
    }
    public static ApiErrorResponse of(ErrorCode errorCode, String path, String detail, String field) {
        String msg = (detail == null) ? errorCode.getMessage() : errorCode.getMessage() + " - " + detail;
        return new ApiErrorResponse(errorCode.getCode(), errorCode.name(), msg,
                errorCode.getHttpStatus(), path, field);
    }
}
