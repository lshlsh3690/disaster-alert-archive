package com.disaster.alert.alertapi.global.exception;

import com.disaster.alert.alertapi.global.dto.ApiErrorResponse;
import com.disaster.alert.alertapi.domain.common.exception.CustomException;
import com.disaster.alert.alertapi.domain.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiErrorResponse> handleCustomException(CustomException e, HttpServletRequest request) {
        ErrorCode ec = e.getErrorCode();
        // 4xx 비즈니스 예외는 정상 흐름이라 로그 노이즈이므로 제외, 5xx(서버측 오류)만 error 로 남긴다.
        if (ec.getHttpStatus() >= 500) {
            log.error("서버 오류 [{}] {} {}", ec.getCode(), request.getRequestURI(), e.getDetail(), e);
        }
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

    // 요청 본문 JSON 을 못 읽는 경우(깨진 JSON, 알 수 없는 enum 값 등) — 클라이언트 입력 문제라 4xx 이고
    // 로그는 남기지 않는다. 이 핸들러가 없으면 아래 catch-all 로 떨어져 500 + error 로그가 났다.
    // 메시지에는 Jackson 내부 상세(타입명·입력값)를 싣지 않고 ErrorCode 의 고정 메시지만 내려준다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e,
            HttpServletRequest request
    ) {
        ErrorCode ec = ErrorCode.JSON_PARSE_ERROR;
        ApiErrorResponse body = ApiErrorResponse.of(ec, request.getRequestURI());
        return ResponseEntity.status(ec.getHttpStatus()).body(body);
    }

    // 권한 없음 — {@code @PreAuthorize} 가 던진 AccessDeniedException(스프링 시큐리티 6.3+ 의 AuthorizationDeniedException 포함,
    // 후자가 전자의 하위 클래스라 이 핸들러 하나로 둘 다 잡힌다). 이 핸들러가 없으면 아래 catch-all 로 떨어져 일반 회원이
    // 관리자 API 를 불렀을 때 403 이 아니라 500 + error 로그가 났다. 정상적인 접근 거부라 warn 으로만 남긴다.
    // 한계: 이 핸들러가 잡으면 예외가 시큐리티 필터(ExceptionTranslationFilter)까지 가지 않는다. 지금 @PreAuthorize 가 붙은
    // 경로는 모두 인증 필수라 익명·만료 토큰은 필터에서 먼저 401 이 나지만, permitAll 경로에 @PreAuthorize 를 붙이면
    // 만료 토큰 사용자가 401 대신 403 을 받아 프론트의 토큰 재발급이 돌지 않는다. 또 필터 단 403 은 SecurityConfig 의
    // {"message":"FORBIDDEN"} 모양이라 응답 모양이 둘이다.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        log.warn("접근 거부 {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(ec.getHttpStatus()).body(ApiErrorResponse.of(ec, request.getRequestURI()));
    }

    // 쿼리·경로 값을 타입으로 바꾸지 못한 경우(날짜 형식 오류, 모르는 enum 값 등) — 클라이언트 입력 문제라 400.
    // 공개 API 에도 걸리므로 깨진 파라미터를 대량으로 보내도 로그가 쌓이지 않게 debug 로 둔다(본문 못 읽음 핸들러와 같은 취지).
    // 없으면 catch-all 로 떨어져 500 + error 로그가 났다. 메시지에는 변환 예외 내부 상세 대신 어느 파라미터인지만 싣는다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                               HttpServletRequest request) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        log.debug("요청 값 형식 오류 {} 파라미터={}", request.getRequestURI(), e.getName());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiErrorResponse.of(ec, request.getRequestURI(), "파라미터 형식이 올바르지 않습니다: " + e.getName()));
    }

    // 필수 쿼리 파라미터가 빠진 경우(예: from 없이 조회) — 형식 오류와 같은 클라이언트 입력 문제라 400 + debug.
    // 없으면 catch-all 로 떨어져 "형식이 틀리면 400, 아예 없으면 500" 이 됐다.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException e,
                                                                   HttpServletRequest request) {
        ErrorCode ec = ErrorCode.INVALID_REQUEST;
        log.debug("필수 파라미터 누락 {} 파라미터={}", request.getRequestURI(), e.getParameterName());
        return ResponseEntity.status(ec.getHttpStatus())
                .body(ApiErrorResponse.of(ec, request.getRequestURI(), "필수 파라미터가 없습니다: " + e.getParameterName()));
    }

    // 알 수 없는 모든 예외 — 미처리 예외는 항상 버그이므로 스택트레이스와 함께 error 로 남긴다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception e, HttpServletRequest request) {
        ErrorCode ec = ErrorCode.INTERNAL_SERVER_ERROR;
        log.error("처리되지 않은 예외 {}", request.getRequestURI(), e);
        ApiErrorResponse body = ApiErrorResponse.of(ec, request.getRequestURI(), e.getMessage());
        return ResponseEntity.status(ec.getHttpStatus()).body(body);
    }
}
