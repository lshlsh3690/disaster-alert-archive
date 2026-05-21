package com.disaster.alert.alertapi.domain.weather.api;

/**
 * KMA ASOS API 의 호출 한도 초과 / 인증 게이트웨이 미반영 등 "당장 재시도해도 소용없는" 응답 시그널.
 *
 * <p>백필러는 이 예외를 catch 하면 전체 루프를 즉시 중단한다.
 * 다음 트리거에서 미완료 셀부터 자연스럽게 이어 처리한다.
 *
 * <p>일반 네트워크/파싱 오류는 이 예외로 감싸지 않는다 — 그것은 셀 단위로만 실패 처리.
 */
public class AsosApiRateLimitException extends RuntimeException {
    public AsosApiRateLimitException(String message) {
        super(message);
    }
}
