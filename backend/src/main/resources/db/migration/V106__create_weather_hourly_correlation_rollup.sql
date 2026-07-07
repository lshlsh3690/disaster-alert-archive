-- 시군구 단위 시간별 alert-날씨 상관 롤업 테이블
--
-- weather_observation(626만 행)을 disasterAlert.createdAt과 EXTRACT() 기반으로 매시 조인하면
-- (연/월/일/시 각각 eq 비교) 인덱스를 못 타 Parallel Seq Scan이 발생하고,
-- LATERAL로 인덱스 스캔을 강제해도 alert-지역 조합 수(약 8.6만)만큼 랜덤 인덱스 조회가 발생해
-- 60초 이상 걸린다 (RDS 버스터블 인스턴스에서 랜덤 I/O 비용이 큼).
--
-- weather_daily_summary처럼 "조밀하게(dense)" 뭉쳐서 줄일 수는 없다 (이미 원본 grain이 시간 단위).
-- 대신 "실제로 alert가 발생한 (시군구, 시각) 조합"만 희소(sparse)하게 적재한다 — 전체 이력에서
-- alert-지역 조합이 최대 8.6만 건이므로 테이블이 아무리 커도 weather_observation보다 훨씬 작다.
--
-- alert_count는 해당 (시군구, 시각)의 전체 alert 수(유형/등급 무관)만 담는다 — type/level/keyword
-- 필터가 걸린 요청은 이 테이블로 answer할 수 없으므로 기존 라이브 쿼리로 폴백한다.

CREATE TABLE IF NOT EXISTS weather_hourly_correlation_rollup (
    id                   BIGSERIAL         NOT NULL,
    legal_district_code  VARCHAR(10)       NOT NULL,
    bucket_hour          TIMESTAMP         NOT NULL,   -- 시간 절삭 (weather_observation.observed_at과 동일 grain)

    alert_count          INTEGER           NOT NULL DEFAULT 0,

    avg_temp             DOUBLE PRECISION,
    avg_precip           DOUBLE PRECISION,
    avg_wind_speed       DOUBLE PRECISION,

    updated_at           TIMESTAMP         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_weather_hourly_correlation_rollup
        PRIMARY KEY (id),
    CONSTRAINT fk_whcr_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code),
    CONSTRAINT uq_whcr_district_hour
        UNIQUE (legal_district_code, bucket_hour)
);

-- 시군구/districtCode 필터 + 시간 range 조회용
CREATE INDEX IF NOT EXISTS idx_whcr_district_hour
    ON weather_hourly_correlation_rollup (legal_district_code, bucket_hour DESC);

-- 전국 단위(시군구 필터 없음) 시간 range 조회용 (getWeatherHourlyCorrelation)
CREATE INDEX IF NOT EXISTS idx_whcr_hour
    ON weather_hourly_correlation_rollup (bucket_hour);
