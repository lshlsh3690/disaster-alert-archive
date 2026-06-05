package com.disaster.alert.alertapi.domain.risk.model;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;
import com.disaster.alert.alertapi.domain.risk.RiskConstants;

/**
 * 재난 위험 점수 값 객체 — 5차원 점수 규칙(합성·정부등급 가중·시간 감쇠·정규화)을 캡슐화.
 *
 * <p>애플리케이션 서비스({@code RiskCalculationService})는 데이터 로딩/영속만 담당하고,
 * 점수 계산 규칙 자체는 이 도메인 값 객체가 소유한다(순수 함수, DB/프레임워크 비의존 → 단위 테스트 용이).
 *
 * <p>{@link #baseScore} 는 시간 감쇠 적용 <b>전</b> 점수 = weight × intensity × severity.
 */
public record RiskScore(double baseScore) {

    /** weight × intensity × severity 합성. */
    public static RiskScore of(double weight, double intensityMultiplier, double severityMultiplier) {
        return new RiskScore(weight * intensityMultiplier * severityMultiplier);
    }

    /**
     * 정부 분류 등급({@link DisasterLevel}) → 가중치.
     * LEVEL_3=위급재난(1.2), LEVEL_2=긴급재난(1.0), LEVEL_1=안전안내(0.6), null=1.0.
     */
    public static double severityMultiplier(DisasterLevel level) {
        if (level == null) return 1.0;
        return switch (level) {
            case LEVEL_3 -> 1.2;
            case LEVEL_2 -> 1.0;
            case LEVEL_1 -> 0.6;
        };
    }

    /**
     * 시간 감쇠 적용 점수. half-life 기준 지수 감쇠 (baseScore × 2^(-Δt/halfLife)).
     *
     * @param hoursElapsed 마지막 알림으로부터 경과 시간(시간 단위). 음수는 0 처리.
     * @param halfLifeHours 반감기(시간).
     */
    public double decayed(long hoursElapsed, int halfLifeHours) {
        double hours = Math.max(0, hoursElapsed);
        double decay = Math.exp(-hours * Math.log(2) / halfLifeHours);
        return baseScore * decay;
    }

    /** 감쇠 점수 → 0~1 정규화 (분모 {@link RiskConstants#NORMALIZE_DIVISOR}). */
    public static double normalize(double decayedScore) {
        return Math.min(1.0, decayedScore / RiskConstants.NORMALIZE_DIVISOR);
    }
}
