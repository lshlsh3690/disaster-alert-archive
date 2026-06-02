package com.disaster.alert.alertapi.domain.risk;

/**
 * 위험도 모듈 공통 상수.
 *
 * <p>감쇠 윈도우/정규화 분모 등은 여러 클래스(서비스·스케줄러·조회)에서 함께 쓰므로
 * 한 곳에서만 정의한다(중복 정의로 인한 불일치 방지).
 */
public final class RiskConstants {

    private RiskConstants() {}

    /** 감쇠가 무의미해지는 윈도우. 이보다 오래된 last_alert_at 이벤트는 집계에서 제외. */
    public static final int ACTIVE_WINDOW_DAYS = 30;

    /** 점수 정규화 분모. raw 최댓값(weight1.5 × intensity1.5 × severity1.2 ≈ 2.7) 고려해 2.0 으로 0~1 압축. */
    public static final double NORMALIZE_DIVISOR = 2.0;

    /** 프로파일에 half-life 가 없을 때 기본값. */
    public static final int DEFAULT_HALF_LIFE_HOURS = 24;

    /** region_risk_history retention. */
    public static final int HISTORY_RETENTION_DAYS = 90;
}
