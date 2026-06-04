CREATE TABLE IF NOT EXISTS weather_daily_summary (
    id                   BIGSERIAL         NOT NULL,
    legal_district_code  VARCHAR(10)       NOT NULL,
    date                 DATE              NOT NULL,

    -- 기온 (°C)
    avg_temp             DOUBLE PRECISION,
    min_temp             DOUBLE PRECISION,
    max_temp             DOUBLE PRECISION,

    -- 강수 (mm)
    total_precip         DOUBLE PRECISION,            -- 일강수량 합계
    max_hourly_precip    DOUBLE PRECISION,            -- 시간 최대 강수량
    precip_hours         INTEGER,                     -- 강수 발생 시간 수

    -- 풍속 (m/s)
    avg_wind_speed       DOUBLE PRECISION,
    max_wind_speed       DOUBLE PRECISION,

    -- 습도 (%)
    avg_humidity         DOUBLE PRECISION,
    min_humidity         DOUBLE PRECISION,

    -- 기압 (hPa)
    avg_pressure         DOUBLE PRECISION,

    -- 데이터 품질: 집계에 사용된 시간 관측 수 (최대 24)
    obs_count            INTEGER           NOT NULL DEFAULT 0,

    created_at           TIMESTAMP         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_weather_daily_summary
        PRIMARY KEY (id),
    CONSTRAINT fk_wds_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code),
    CONSTRAINT uq_wds_district_date
        UNIQUE (legal_district_code, date)
);

CREATE INDEX IF NOT EXISTS idx_wds_district_date
    ON weather_daily_summary (legal_district_code, date DESC);

CREATE INDEX IF NOT EXISTS idx_wds_date
    ON weather_daily_summary (date);
