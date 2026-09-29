-- 통계 API 캐시 성능 측정용 합성 시드 데이터.
--
-- 운영 데이터가 아니라 순수 합성 데이터다. disaster_alert_id를 10,000,000 이상의
-- 전용 대역에서만 사용하므로 disaster_alert_seq(1부터 500씩 증가)가 만드는 실 데이터
-- ID와 절대 충돌하지 않는다 — 로컬 개발 DB 외에는 어디에도 실행하지 말 것.
--
-- 재실행하면 이전에 삽입한 행 위에 새 행을 이어서 추가한다(멱등 append) — 데이터를
-- 2~3배로 늘리고 싶으면 이 스크립트를 그대로 다시 실행하면 된다.
--
-- 실행:
--   docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB \
--     < backend/loadtest/seed/seed_stats_data.sql
-- (.env.dev를 source해서 POSTGRES_USER/POSTGRES_DB를 미리 export해둘 것)

BEGIN;

-- ── 0. 이번 실행에서 채울 범위 결정 ─────────────────────────────
-- 기존 합성 데이터(10,000,000 이상) 중 최댓값 다음부터 이어서 채운다.
CREATE TEMP TABLE _seed_params AS
SELECT
    COALESCE(MAX(disaster_alert_id), 10000000) AS base_id,
    60000                                        AS row_count
FROM disaster_alert
WHERE disaster_alert_id >= 10000000;

-- ── 1. disaster_alert ────────────────────────────────────────
-- emergency_level: LEVEL_1(안전안내) 70% / LEVEL_2(긴급재난) 20% / LEVEL_3(위급재난) 10%
--   — 실제로도 안전안내 문자가 압도적으로 많다.
-- disaster_type: application.yml의 clustering 설정 주석에 등장하는 실제 유형 문자열 사용.
-- embedding은 NULL로 둔다 — 통계 쿼리가 쓰지 않고, 채우면 HNSW 인덱스 빌드 비용만 늘어난다.
INSERT INTO disaster_alert (disaster_alert_id, sn, message, created_at, emergency_level, disaster_type)
SELECT
    p.base_id + gs,
    900000000 + p.base_id + gs,
    'synthetic seed alert #' || gs,
    now() - (random() * interval '1095 days'),
    CASE
        WHEN random() < 0.7 THEN 'LEVEL_1'
        WHEN random() < 0.9 THEN 'LEVEL_2'
        ELSE 'LEVEL_3'
        END,
    (ARRAY['호우', '대설', '강풍', '폭염', '한파', '산불', '산사태', '홍수',
        '화재', '붕괴', '교통사고', '지진', '실종', '기타'])[1 + floor(random() * 14)::int]
FROM _seed_params p, generate_series(1, p.row_count) AS gs;

-- ── 2. disaster_alert_region ─────────────────────────────────
-- 기존 legal_district에서 "시군구 레벨" 코드만 후보로 삼는다.
--   시도 레벨: 뒤 8자리가 0 (예: 1200000000)
--   시군구 레벨: 뒤 5자리가 0이지만 시도 레벨은 아님 (예: 1211000000)
CREATE TEMP TABLE _sigungu AS
SELECT code, row_number() OVER (ORDER BY code) AS rn
FROM legal_district
WHERE is_active = true
  AND code LIKE '%00000'
  AND code NOT LIKE '%00000000';

CREATE TEMP TABLE _sigungu_count AS
SELECT count(*) AS cnt FROM _sigungu;

-- alert당 시군구 1개를 배정한다. random()을 join 조건에서 직접 비교하면 후보 행마다
-- 재평가되어 매칭이 깨지므로, 서브쿼리에서 먼저 난수를 확정(rn)한 뒤 그 값으로 조인한다.
INSERT INTO disaster_alert_region (disaster_alert_id, legal_district_code)
SELECT picks.disaster_alert_id, s.code
FROM (
    SELECT da.disaster_alert_id,
           1 + floor(random() * c.cnt)::int AS rn
    FROM disaster_alert da
             CROSS JOIN _sigungu_count c
    WHERE da.disaster_alert_id > (SELECT base_id FROM _seed_params)
) picks
         JOIN _sigungu s ON s.rn = picks.rn;

-- 15% 확률로 2번째 시군구도 배정 (다지역 알림 시뮬레이션). 같은 코드가 뽑히면 PK 충돌이므로 무시.
INSERT INTO disaster_alert_region (disaster_alert_id, legal_district_code)
SELECT picks.disaster_alert_id, s.code
FROM (
    SELECT da.disaster_alert_id,
           1 + floor(random() * c.cnt)::int AS rn
    FROM disaster_alert da
             CROSS JOIN _sigungu_count c
    WHERE da.disaster_alert_id > (SELECT base_id FROM _seed_params)
      AND random() < 0.15
) picks
         JOIN _sigungu s ON s.rn = picks.rn
ON CONFLICT (disaster_alert_id, legal_district_code) DO NOTHING;

-- ── 3. weather_daily_summary ─────────────────────────────────
-- 합성 시드 불필요: Flyway 자체 백필 마이그레이션(분기별 weather history seed)이 이미
-- 2023-09-01~2026-05-21, 시군구 264개 후보 중 225개(85%)를 실데이터로 채워뒀다
-- (docker exec ... psql로 사전 확인). getWeatherCorrelation 등의 LEFT JOIN이 실제로
-- 매칭될 데이터가 충분하므로 이 섹션은 만들지 않는다.

-- ── 4. 결과 요약 ──────────────────────────────────────────────
SELECT 'disaster_alert' AS table_name, count(*) AS row_count FROM disaster_alert
UNION ALL
SELECT 'disaster_alert_region', count(*) FROM disaster_alert_region
UNION ALL
SELECT 'weather_daily_summary', count(*) FROM weather_daily_summary
UNION ALL
SELECT 'sigungu 후보 legal_district 수', count(*) FROM _sigungu;

COMMIT;
