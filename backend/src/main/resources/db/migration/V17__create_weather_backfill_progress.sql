-- ASOS 시간자료 백필의 진행 상태를 (월, 지점) 단위로 기록한다.
--
-- 백필러는 시작 시 status != 'DONE' 인 (year_month, stn_id) 셀만 수행 → 재개 자연스럽게 처리.
-- API 차단·네트워크 오류로 중간에 멈춰도 다음 트리거에서 미완료분부터 이어 처리.
--
-- 한 셀 분량: 1지점 × 1개월 × 24시간 ≈ 720 row (시군구 fan-out 후 약 1,900 row)
-- 전체 크기: 92지점 × 33개월(2023.09~2026.05) = 약 3,036 셀.

CREATE TABLE weather_backfill_progress (
    year_month   VARCHAR(7)   NOT NULL,   -- 'YYYY-MM' 형식
    stn_id       VARCHAR(10)  NOT NULL,   -- ASOS 지점번호 (예: '108')
    status       VARCHAR(20)  NOT NULL,   -- PENDING / IN_PROGRESS / DONE / FAILED
    row_count    INTEGER,                 -- 적재 성공 시 저장한 시군구 row 수
    error_msg    TEXT,
    started_at   TIMESTAMP,
    finished_at  TIMESTAMP,
    PRIMARY KEY (year_month, stn_id)
);

-- 상태별 카운트/검색용
CREATE INDEX idx_wbp_status ON weather_backfill_progress (status);
