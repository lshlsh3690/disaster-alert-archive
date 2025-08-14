package com.disaster.alert.alertapi.domain.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    INVALID_REQUEST(400, "C001", "잘못된 요청입니다."),
    VALIDATION_FAILED(400, "C002", "요청 값 검증에 실패했습니다."),
    JSON_PARSE_ERROR(400, "C003", "요청 본문을 해석할 수 없습니다."),

    AUTH_INVALID_CREDENTIALS(401, "A101", "이메일 또는 비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(400, "A102", "이메일 인증이 완료되지 않았습니다."),
    PASSWORD_MISMATCH(400, "A103", "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    INVALID_EMAIL_VERIFICATION_CODE(400, "A104", "인증 코드가 일치하지 않거나 만료되었습니다."),
    INVALID_REFRESH_TOKEN(401, "A105", "Refresh Token이 유효하지 않습니다."),
    REFRESH_TOKEN_NOT_FOUND(404, "A106", "Refresh Token을 찾을 수 없습니다."),
    UNAUTHORIZED(401, "A107", "인증되지 않은 사용자입니다."),


    DUPLICATE_EMAIL(409, "M401", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(409, "M402", "이미 사용 중인 닉네임입니다."),

    MEMBER_NOT_FOUND(404, "M404", "회원을 찾을 수 없습니다."),


    INTERNAL_SERVER_ERROR(500, "C500", "서버 내부 오류입니다."),
    RANDOM_CODE_GENERATION_FAILED(500, "C501", "랜덤 코드 생성에 실패했습니다.")
    ;

    private final int httpStatus;
    private final String code;
    private final String message;

    ErrorCode(int httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
