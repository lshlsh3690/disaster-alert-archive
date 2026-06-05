CREATE TABLE region_risk_daily (
    region_code VARCHAR(20) NOT NULL,
    snapshot_date DATE NOT NULL,
    risk_score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (region_code, snapshot_date)
);

COMMENT ON TABLE region_risk_daily IS '일별 지역 최고 위험도 요약 (다운샘플링)';
COMMENT ON COLUMN region_risk_daily.region_code IS '시군구 법정동 코드';
COMMENT ON COLUMN region_risk_daily.snapshot_date IS '요약된 날짜 (Daily)';
COMMENT ON COLUMN region_risk_daily.risk_score IS '해당 날짜의 최고 위험도 점수';
