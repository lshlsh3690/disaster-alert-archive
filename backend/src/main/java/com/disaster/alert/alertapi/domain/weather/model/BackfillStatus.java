package com.disaster.alert.alertapi.domain.weather.model;

/**
 * 백필 (year_month × stn_id) 셀의 진행 상태.
 *
 * <p>{@code DONE} 인 셀은 백필 트리거 재호출 시 건너뜀 — 재개 자연 처리.
 * {@code FAILED} 는 다음 트리거에서 다시 시도된다.
 */
public enum BackfillStatus {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED
}
