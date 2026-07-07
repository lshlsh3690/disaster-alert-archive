-- weather_hourly_correlation_rollup 과거 데이터 백필.
-- V101(weather_daily_summary 백필)과 동일하게 weather_observation 이력이 있는 구간(2023-09-01~)만 대상으로 한다.
-- 1회성 배포 마이그레이션이므로 기존 라이브 쿼리와 동일한 비용(EXTRACT 기반 조인)이 들어도 무방하다.

INSERT INTO weather_hourly_correlation_rollup (
    legal_district_code, bucket_hour,
    alert_count, avg_temp, avg_precip, avg_wind_speed
)
SELECT
    ld.code                                            AS legal_district_code,
    date_trunc('hour', da.created_at)                  AS bucket_hour,
    COUNT(DISTINCT da.disaster_alert_id)                AS alert_count,
    AVG(wo.temperature)                                AS avg_temp,
    AVG(wo.precipitation)                              AS avg_precip,
    AVG(wo.wind_speed)                                 AS avg_wind_speed
FROM disaster_alert da
JOIN disaster_alert_region dar ON dar.disaster_alert_id = da.disaster_alert_id
JOIN legal_district ld ON ld.code = dar.legal_district_code
LEFT JOIN weather_observation wo
    ON wo.legal_district_code = ld.code
   AND wo.observed_at = date_trunc('hour', da.created_at)
WHERE da.created_at >= '2023-09-01'
GROUP BY ld.code, date_trunc('hour', da.created_at)
ON CONFLICT (legal_district_code, bucket_hour)
DO UPDATE SET
    alert_count    = EXCLUDED.alert_count,
    avg_temp       = EXCLUDED.avg_temp,
    avg_precip     = EXCLUDED.avg_precip,
    avg_wind_speed = EXCLUDED.avg_wind_speed,
    updated_at     = NOW();
