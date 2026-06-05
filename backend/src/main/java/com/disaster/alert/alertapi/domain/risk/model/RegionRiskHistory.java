package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 지역 위험도 시계열 스냅샷.
 *
 * <p>1시간마다 nonzero 지역의 risk_score 를 기록. Phase 2 에서 Chronos-Bolt forecasting
 * 입력 데이터로 사용 (충분한 history 축적 후 SageMaker 배치 추론).
 */
@Entity
@Table(name = "region_risk_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionRiskHistory {

    @EmbeddedId
    private RegionRiskHistoryId id;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    public static RegionRiskHistory of(String regionCode, LocalDateTime snapshotAt, double riskScore) {
        RegionRiskHistory h = new RegionRiskHistory();
        h.id = new RegionRiskHistoryId(regionCode, snapshotAt);
        h.riskScore = riskScore;
        return h;
    }
}
