-- V108~V110은 "과거 데이터는 기록 당시 유효했던 옛 코드(29xx/46xx)를 그대로 유지한다"는
-- 원칙으로 legal_district/region_adjacency만 신규 코드로 확장하고, 재난문자·이벤트·위험도
-- 데이터 자체는 건드리지 않았다. 이번 마이그레이션은 그 원칙을 뒤집어, 광주광역시·전라남도의
-- 재난문자/이벤트/위험도 이력 데이터를 전남광주통합특별시(12xx) 코드로 전부 통합한다.
--
-- 기상 관측 이력(weather_observation/weather_daily_summary/weather_hourly_correlation_rollup)은
-- 관측소 기준 실측 데이터라는 별개 성격이라 이번 마이그레이션 대상에서 제외한다(V109와 동일 원칙 유지).
--
-- 대상 6개 테이블은 두 그룹으로 나뉜다.
--   A) 10자리 법정동코드 컬럼(legal_district FK) — legal_district.name 매칭으로 신규 코드 조회
--      : disaster_alert_region, user_disaster_alert_region, guest_fcm_region, event_region_impact
--   B) 5자리 시군구코드 컬럼(FK 없음, risk 도메인 집계) — V110에서 이미 확정한 27개 시군구
--      1:1 매핑을 임시 테이블로 재사용 : region_risk_index, region_risk_history, region_risk_daily

CREATE TEMP TABLE _sigungu_code_map (old_code VARCHAR(5) PRIMARY KEY, new_code VARCHAR(5) NOT NULL);
INSERT INTO _sigungu_code_map (old_code, new_code) VALUES
('29110','12210'), ('29140','12240'), ('29155','12270'), ('29170','12300'), ('29200','12330'),
('46110','12110'), ('46130','12130'), ('46150','12150'), ('46170','12170'), ('46230','12190'),
('46710','12710'), ('46720','12720'), ('46730','12730'), ('46770','12740'), ('46780','12750'),
('46790','12760'), ('46800','12770'), ('46810','12780'), ('46820','12790'), ('46830','12800'),
('46840','12810'), ('46860','12820'), ('46870','12830'), ('46880','12840'), ('46890','12850'),
('46900','12860'), ('46910','12870');

-- =========================================================================
-- A-1) disaster_alert_region (복합 PK: disaster_alert_id, legal_district_code)
-- =========================================================================
INSERT INTO disaster_alert_region (disaster_alert_id, legal_district_code)
SELECT dar.disaster_alert_id, new_ld.code
FROM disaster_alert_region dar
JOIN legal_district old_ld ON old_ld.code = dar.legal_district_code
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
ON CONFLICT (disaster_alert_id, legal_district_code) DO NOTHING;

DELETE FROM disaster_alert_region dar
USING legal_district old_ld
WHERE dar.legal_district_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- =========================================================================
-- A-2) user_disaster_alert_region (복합 PK: user_disaster_alert_id, legal_district_code)
-- =========================================================================
INSERT INTO user_disaster_alert_region (user_disaster_alert_id, legal_district_code)
SELECT udar.user_disaster_alert_id, new_ld.code
FROM user_disaster_alert_region udar
JOIN legal_district old_ld ON old_ld.code = udar.legal_district_code
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
ON CONFLICT (user_disaster_alert_id, legal_district_code) DO NOTHING;

DELETE FROM user_disaster_alert_region udar
USING legal_district old_ld
WHERE udar.legal_district_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- =========================================================================
-- A-3) guest_fcm_region (UNIQUE(fcm_token, legal_district_code), PK는 별도 id)
-- =========================================================================
INSERT INTO guest_fcm_region (fcm_token, legal_district_code, created_at)
SELECT gfr.fcm_token, new_ld.code, gfr.created_at
FROM guest_fcm_region gfr
JOIN legal_district old_ld ON old_ld.code = gfr.legal_district_code
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
ON CONFLICT (fcm_token, legal_district_code) DO NOTHING;

DELETE FROM guest_fcm_region gfr
USING legal_district old_ld
WHERE gfr.legal_district_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- =========================================================================
-- A-4) disaster_events.primary_region_code / primary_region_name (FK 없는 캐시 컬럼, 단일 UPDATE)
-- =========================================================================
UPDATE disaster_events de
SET primary_region_code = new_ld.code,
    primary_region_name = new_ld.name
FROM legal_district old_ld
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE de.primary_region_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- =========================================================================
-- A-5) event_region_impact (복합 PK: event_id, region_code — 10자리 법정동코드)
-- =========================================================================
-- 시도 루트 코드(광주광역시/전라남도)가 동일 이벤트에 함께 걸린 경우 같은 신규 코드로
-- 몰릴 수 있어, INSERT 전에 (event_id, 신규 region_code) 기준으로 먼저 집계한다
-- ("ON CONFLICT DO UPDATE command cannot affect row a second time" 방지).
INSERT INTO event_region_impact (event_id, region_code, impact_score, created_at)
SELECT event_id, region_code, MAX(impact_score), MIN(created_at)
FROM (
    SELECT eri.event_id, new_ld.code AS region_code, eri.impact_score, eri.created_at
    FROM event_region_impact eri
    JOIN legal_district old_ld ON old_ld.code = eri.region_code
    JOIN legal_district new_ld ON new_ld.is_active = true
        AND new_ld.name = (
            CASE
                WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
                WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
                WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
            END
        )
    WHERE old_ld.is_active = false
      AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%')
) merged
GROUP BY event_id, region_code
ON CONFLICT (event_id, region_code) DO UPDATE
    SET impact_score = GREATEST(event_region_impact.impact_score, EXCLUDED.impact_score);

DELETE FROM event_region_impact eri
USING legal_district old_ld
WHERE eri.region_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- =========================================================================
-- B-1) region_risk_index (PK: region_code, 5자리 시군구코드 — 현재 스냅샷 성격)
-- 이미 신규 코드로 실시간 갱신된 행이 존재할 수 있으므로, 신규 코드 행이 없을 때만
-- 옛 행을 이관(더 최신인 신규 라이브 데이터를 옛 스냅샷으로 덮어쓰지 않기 위함).
-- =========================================================================
INSERT INTO region_risk_index (region_code, risk_score, top_event_id, updated_at, source_score, source_top_event_id)
SELECT m.new_code, ri.risk_score, ri.top_event_id, ri.updated_at, ri.source_score, ri.source_top_event_id
FROM region_risk_index ri
JOIN _sigungu_code_map m ON m.old_code = ri.region_code
WHERE NOT EXISTS (SELECT 1 FROM region_risk_index ri2 WHERE ri2.region_code = m.new_code)
ON CONFLICT (region_code) DO NOTHING;

DELETE FROM region_risk_index
WHERE region_code IN (SELECT old_code FROM _sigungu_code_map);

-- =========================================================================
-- B-2) region_risk_history (PK: region_code, snapshot_at — append-only 이력)
-- =========================================================================
UPDATE region_risk_history rh
SET region_code = m.new_code
FROM _sigungu_code_map m
WHERE rh.region_code = m.old_code
  AND NOT EXISTS (
      SELECT 1 FROM region_risk_history rh2
      WHERE rh2.region_code = m.new_code AND rh2.snapshot_at = rh.snapshot_at
  );

-- 동일 (신규코드, snapshot_at) 이 이미 존재해 위 UPDATE로 옮기지 못한 극히 드문 충돌 행 정리
DELETE FROM region_risk_history
WHERE region_code IN (SELECT old_code FROM _sigungu_code_map);

-- =========================================================================
-- B-3) region_risk_daily (PK: region_code, snapshot_date — 일별 다운샘플링)
-- =========================================================================
UPDATE region_risk_daily rd
SET region_code = m.new_code
FROM _sigungu_code_map m
WHERE rd.region_code = m.old_code
  AND NOT EXISTS (
      SELECT 1 FROM region_risk_daily rd2
      WHERE rd2.region_code = m.new_code AND rd2.snapshot_date = rd.snapshot_date
  );

DELETE FROM region_risk_daily
WHERE region_code IN (SELECT old_code FROM _sigungu_code_map);

DROP TABLE _sigungu_code_map;
