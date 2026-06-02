-- 재난 위험도 모델 인프라
--
-- 목표: disaster_events(클러스터링 결과) 위에 event 단위 위험도를 산출.
-- 5차원: weight[유형] × intensity[강도] × severity[정부등급] × decay(시간) → 영향 법정동 집합
--
-- 핵심 설계(검증 반영):
--  - 공간 확산은 인접 그래프로 추론하지 않는다. 정부 broadcast 영향 법정동을 그대로 쓴다.
--  - 알림 경로는 event_region_impact 기록까지만. region_risk_index 갱신은 스케줄러 전담.
--  - 결합은 MAX, 최종 점수는 /2.0 정규화로 0~1 보장.
--
-- 컬럼 타입 주의: 점수/가중치는 엔티티 필드가 double 이므로 DOUBLE PRECISION 으로 둔다.
-- (DECIMAL/numeric 으로 두면 ddl-auto=validate 에서 float8↔numeric 불일치로 부팅 실패)

-- 1. 유형별 위험 프로파일 (35종 + LLM 생성분)
CREATE TABLE disaster_risk_profile (
    disaster_type      VARCHAR(50)      PRIMARY KEY,
    base_weight        DOUBLE PRECISION NOT NULL,        -- 1.0 기준
    half_life_hours    INT              NOT NULL,        -- 시간 감쇠 반감기
    is_llm_generated   BOOLEAN          NOT NULL DEFAULT FALSE,
    operator_confirmed BOOLEAN          NOT NULL DEFAULT FALSE,
    updated_at         TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- 2. 강도 구간 → multiplier
-- 본문에서 추출한 강도 수치(규모/mm/도/ha 등)를 구간 매핑하여 weight 보정.
CREATE TABLE intensity_bracket (
    id            BIGSERIAL        PRIMARY KEY,
    disaster_type VARCHAR(50)      NOT NULL,
    min_value     DOUBLE PRECISION NOT NULL,            -- 구간 하한(포함)
    max_value     DOUBLE PRECISION NOT NULL,            -- 구간 상한(미포함)
    unit          VARCHAR(20),                          -- 규모/mm/도/ha 등 (문서화용)
    multiplier    DOUBLE PRECISION NOT NULL,
    label         VARCHAR(50),
    CONSTRAINT uq_intensity_bracket UNIQUE (disaster_type, min_value)
);
CREATE INDEX idx_intensity_bracket_type ON intensity_bracket(disaster_type);

-- 3. 이벤트 → 법정동 영향 (alert 아니라 event 기준 — 이중 카운팅 방지)
-- impact_score 는 감쇠 적용 전 baseScore. 감쇠는 읽을 때 event.last_alert_at 기준으로 계산.
CREATE TABLE event_region_impact (
    event_id     BIGINT           NOT NULL REFERENCES disaster_events(id) ON DELETE CASCADE,
    region_code  VARCHAR(20)      NOT NULL,              -- 법정동 코드
    impact_score DOUBLE PRECISION NOT NULL,
    created_at   TIMESTAMP        NOT NULL DEFAULT NOW(),
    PRIMARY KEY (event_id, region_code)
);
CREATE INDEX idx_eri_region ON event_region_impact(region_code);

-- 4. 지역별 현재 위험도 (시군구 단위 — FE 히트맵 D5 기준)
-- 법정동 impact 를 시군구(코드 앞 5자리)로 집계한 결과. 0~1 정규화 점수.
CREATE TABLE region_risk_index (
    region_code  VARCHAR(20)      PRIMARY KEY,           -- 시군구 코드
    risk_score   DOUBLE PRECISION NOT NULL,
    top_event_id BIGINT           REFERENCES disaster_events(id),
    updated_at   TIMESTAMP        NOT NULL DEFAULT NOW()
);

-- 5. 시계열 이력 (Phase 2 Chronos forecasting 용 데이터 축적)
CREATE TABLE region_risk_history (
    region_code VARCHAR(20)      NOT NULL,               -- 시군구 코드
    snapshot_at TIMESTAMP        NOT NULL,
    risk_score  DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (region_code, snapshot_at)
);
CREATE INDEX idx_risk_history_time ON region_risk_history(snapshot_at);
