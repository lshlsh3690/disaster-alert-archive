-- 시군구 단위 시간별 날씨 관측 테이블
--
-- 한 row = (시군구, 시각) 에 대한 1시간 단위 관측/예보 값.
-- 과거(ASOS_HISTORY) / 단기예보(KMA_FORECAST) / 초단기실황(KMA_NOWCAST) 데이터를 모두 한 테이블에 적재한다.
-- 사용처:
--   1) 재난 정보 상세 응답에 "발령 시점 날씨" 함께 노출
--   2) AI 위험도 예측 모델의 입력 변수
--
-- 데이터 규모 추산: 시군구 ~250 × 시간 24 × 365 ≈ 220만 row / 년
-- 5년 누적 ~1,100만 row 까지 PostgreSQL 에서 인덱스로 충분히 다룰 수 있음.

CREATE TABLE IF NOT EXISTS weather_observation (
    id                   BIGSERIAL         NOT NULL,
    legal_district_code  VARCHAR(10)       NOT NULL,
    observed_at          TIMESTAMP         NOT NULL,

    -- 표준 6변수 (모두 nullable — 관측소/예보 모델에 따라 결측 가능)
    temperature          DOUBLE PRECISION,            -- 기온 (°C)
    precipitation        DOUBLE PRECISION,            -- 강수량 (mm)
    wind_speed           DOUBLE PRECISION,            -- 풍속 (m/s)
    wind_direction       INTEGER,                     -- 풍향 (deg, 0-360)
    humidity             DOUBLE PRECISION,            -- 상대습도 (%)
    pressure             DOUBLE PRECISION,            -- 해면기압 (hPa)

    source               VARCHAR(20)       NOT NULL,  -- ASOS_HISTORY | KMA_FORECAST | KMA_NOWCAST
    created_at           TIMESTAMP         NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_weather_observation
        PRIMARY KEY (id),
    CONSTRAINT fk_wo_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code),
    -- 같은 (시군구, 시각) 중복 적재 원천 차단.
    -- 백필이나 cron 이 같은 시각 데이터를 다시 INSERT 해도 안전.
    CONSTRAINT uq_wo_district_time
        UNIQUE (legal_district_code, observed_at)
);

-- 시군구별 시간 범위 조회용 (재난 정보 응답에 날씨 붙일 때 핵심 경로).
-- WHERE legal_district_code = ? AND observed_at BETWEEN ? AND ?
CREATE INDEX IF NOT EXISTS idx_wo_district_observed
    ON weather_observation (legal_district_code, observed_at DESC);

-- 시간 범위 전체 스캔용 (전 시군구의 같은 시각 데이터 조회/통계).
CREATE INDEX IF NOT EXISTS idx_wo_observed_at
    ON weather_observation (observed_at);
