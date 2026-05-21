package com.disaster.alert.alertapi.domain.weather.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * {@link WeatherBackfillProgress} 의 복합 PK — (year_month, stn_id).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class WeatherBackfillProgressId implements Serializable {

    /** 'YYYY-MM' 형식. */
    @Column(name = "year_month", length = 7, nullable = false)
    private String yearMonth;

    /** ASOS 지점 번호 (예: "108"). */
    @Column(name = "stn_id", length = 10, nullable = false)
    private String stnId;

    public static WeatherBackfillProgressId of(String yearMonth, String stnId) {
        return new WeatherBackfillProgressId(yearMonth, stnId);
    }
}
