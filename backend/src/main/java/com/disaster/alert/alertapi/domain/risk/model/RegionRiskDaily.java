package com.disaster.alert.alertapi.domain.risk.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "region_risk_daily")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegionRiskDaily {

    @EmbeddedId
    private RegionRiskDailyId id;

    @Column(name = "risk_score", nullable = false)
    private double riskScore;

    private RegionRiskDaily(String regionCode, LocalDate snapshotDate, double riskScore) {
        this.id = new RegionRiskDailyId(regionCode, snapshotDate);
        this.riskScore = riskScore;
    }

    public static RegionRiskDaily of(String regionCode, LocalDate snapshotDate, double riskScore) {
        return new RegionRiskDaily(regionCode, snapshotDate, riskScore);
    }
}
