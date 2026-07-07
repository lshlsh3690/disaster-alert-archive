package com.disaster.alert.alertapi.domain.weather.model;

import com.disaster.alert.alertapi.domain.legaldistrict.model.LegalDistrict;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시/군/구 단위 시간별 alert-날씨 상관 롤업.
 *
 * <p><b>희소(sparse) 테이블</b>: weather_observation과 달리 모든 (시군구, 시각) 조합이 아니라
 * 실제로 alert가 발생한 조합만 담는다. alert_count는 유형/등급 무관 전체 건수이므로,
 * type/level/keyword 필터가 걸린 조회는 이 테이블로 답할 수 없고 라이브 쿼리로 폴백해야 한다.
 */
@Entity
@Table(name = "weather_hourly_correlation_rollup",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_whcr_district_hour",
               columnNames = {"legal_district_code", "bucket_hour"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WeatherHourlyCorrelationRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legal_district_code", length = 10, nullable = false)
    private String legalDistrictCode;

    /** 시간 절삭된 시각 (weather_observation.observed_at과 동일 grain). */
    @Column(name = "bucket_hour", nullable = false)
    private LocalDateTime bucketHour;

    @Column(name = "alert_count", nullable = false)
    private int alertCount;

    @Column(name = "avg_temp")
    private Double avgTemp;

    @Column(name = "avg_precip")
    private Double avgPrecip;

    @Column(name = "avg_wind_speed")
    private Double avgWindSpeed;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_district_code", insertable = false, updatable = false)
    private LegalDistrict legalDistrict;
}
