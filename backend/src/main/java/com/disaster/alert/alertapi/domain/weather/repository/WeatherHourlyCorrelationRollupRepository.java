package com.disaster.alert.alertapi.domain.weather.repository;

import com.disaster.alert.alertapi.domain.weather.model.WeatherHourlyCorrelationRollup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * weather_hourly_correlation_rollup 갱신 전용 레포지토리.
 *
 * <p>이 테이블은 희소(sparse)하게 유지해야 성능 이점이 있으므로 (alert가 없는 시군구·시각은
 * 행 자체가 없어야 함), 두 갱신 경로 모두 "행이 있으면 갱신, 없으면 alert 쪽에서만 생성"
 * 원칙을 지킨다.
 */
public interface WeatherHourlyCorrelationRollupRepository
        extends JpaRepository<WeatherHourlyCorrelationRollup, Long> {

    /**
     * 신규 alert 저장 직후 호출.
     *
     * <p>해당 (시군구, 시간버킷)만 좁게 재계산해 upsert — alert_count는 그 한 시간 범위로 한정된
     * COUNT(DISTINCT)라 저렴하고, weather 값은 이미 수집돼 있으면 채우고 아직이면 NULL로 두어
     * {@link #syncWeather} 가 나중에 채우도록 한다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO weather_hourly_correlation_rollup (
                legal_district_code, bucket_hour, alert_count, avg_temp, avg_precip, avg_wind_speed
            )
            SELECT
                v.code,
                v.hour,
                (SELECT COUNT(DISTINCT da.disaster_alert_id)
                 FROM disaster_alert da
                 JOIN disaster_alert_region dar ON dar.disaster_alert_id = da.disaster_alert_id
                 WHERE dar.legal_district_code = v.code
                   AND da.created_at >= v.hour
                   AND da.created_at < v.hour + INTERVAL '1 hour'),
                wo.temperature,
                wo.precipitation,
                wo.wind_speed
            FROM (VALUES (CAST(:legalDistrictCode AS VARCHAR(10)), CAST(:bucketHour AS TIMESTAMP))) AS v(code, hour)
            LEFT JOIN weather_observation wo
                ON wo.legal_district_code = v.code
               AND wo.observed_at = v.hour
            ON CONFLICT (legal_district_code, bucket_hour)
            DO UPDATE SET
                alert_count    = EXCLUDED.alert_count,
                avg_temp       = EXCLUDED.avg_temp,
                avg_precip     = EXCLUDED.avg_precip,
                avg_wind_speed = EXCLUDED.avg_wind_speed,
                updated_at     = NOW()
            """, nativeQuery = true)
    void upsertFromAlerts(@Param("legalDistrictCode") String legalDistrictCode, @Param("bucketHour") LocalDateTime bucketHour);

    /**
     * 날씨 수집 직후 호출. 이미 alert로 인해 생성된 롤업 행의 weather 필드만 채운다
     * (alert가 그 시각 날씨 수집보다 먼저 들어와 avg_* 가 NULL인 경우 대응).
     * 행이 없으면 아무 것도 하지 않는다 — 여기서 새로 만들면 테이블이 조밀해져 버린다.
     */
    @Modifying
    @Query("""
            UPDATE WeatherHourlyCorrelationRollup r
            SET r.avgTemp = :temperature, r.avgPrecip = :precipitation, r.avgWindSpeed = :windSpeed,
                r.updatedAt = CURRENT_TIMESTAMP
            WHERE r.legalDistrictCode = :legalDistrictCode AND r.bucketHour = :bucketHour
            """)
    void syncWeather(@Param("legalDistrictCode") String legalDistrictCode,
                      @Param("bucketHour") LocalDateTime bucketHour,
                      @Param("temperature") Double temperature,
                      @Param("precipitation") Double precipitation,
                      @Param("windSpeed") Double windSpeed);
}
