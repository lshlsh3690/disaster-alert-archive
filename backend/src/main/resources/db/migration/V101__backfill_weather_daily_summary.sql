INSERT INTO weather_daily_summary (
    legal_district_code, date,
    avg_temp, min_temp, max_temp,
    total_precip, max_hourly_precip, precip_hours,
    avg_wind_speed, max_wind_speed,
    avg_humidity, min_humidity,
    avg_pressure,
    obs_count
)
SELECT
    legal_district_code,
    CAST(observed_at AS DATE)                          AS date,
    AVG(temperature)                                   AS avg_temp,
    MIN(temperature)                                   AS min_temp,
    MAX(temperature)                                   AS max_temp,
    SUM(precipitation)                                 AS total_precip,
    MAX(precipitation)                                 AS max_hourly_precip,
    COUNT(CASE WHEN precipitation > 0 THEN 1 END)      AS precip_hours,
    AVG(wind_speed)                                    AS avg_wind_speed,
    MAX(wind_speed)                                    AS max_wind_speed,
    AVG(humidity)                                      AS avg_humidity,
    MIN(humidity)                                      AS min_humidity,
    AVG(pressure)                                      AS avg_pressure,
    COUNT(*)                                           AS obs_count
FROM weather_observation
WHERE CAST(observed_at AS DATE) >= '2023-09-01'
GROUP BY legal_district_code, CAST(observed_at AS DATE)
ON CONFLICT (legal_district_code, date)
DO UPDATE SET
    avg_temp          = EXCLUDED.avg_temp,
    min_temp          = EXCLUDED.min_temp,
    max_temp          = EXCLUDED.max_temp,
    total_precip      = EXCLUDED.total_precip,
    max_hourly_precip = EXCLUDED.max_hourly_precip,
    precip_hours      = EXCLUDED.precip_hours,
    avg_wind_speed    = EXCLUDED.avg_wind_speed,
    max_wind_speed    = EXCLUDED.max_wind_speed,
    avg_humidity      = EXCLUDED.avg_humidity,
    min_humidity      = EXCLUDED.min_humidity,
    avg_pressure      = EXCLUDED.avg_pressure,
    obs_count         = EXCLUDED.obs_count;
