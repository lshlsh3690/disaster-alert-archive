-- V108이 전남·광주 통합특별시 개편으로 광주광역시(29xx)·전라남도(46xx) 코드를 폐지 처리하면서,
-- 그 코드를 참조하던 "미래 지향적" 테이블(사용자 관심지역 구독, 기상관측소 라우팅 설정)이
-- 신규 재난문자·기상관측과 연결이 끊기는 문제를 함께 해소한다.
--
-- weather_observation/weather_daily_summary/weather_hourly_correlation_rollup 등 과거 실측
-- 데이터 테이블은 대상에서 제외한다 — 그 데이터는 기록 당시 실제 유효했던 옛 코드를 그대로
-- 유지하는 것이 맞다(disaster_alert_region의 2026-07-01 이전 alert들과 동일한 원칙).

-- 1) member_favorite_region: 신규 코드로 추가 후 옛 코드 행 삭제 (PK 충돌 시 무시)
INSERT INTO member_favorite_region (member_id, legal_district_code, created_at)
SELECT mfr.member_id, new_ld.code, mfr.created_at
FROM member_favorite_region mfr
JOIN legal_district old_ld ON old_ld.code = mfr.legal_district_code
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
ON CONFLICT (member_id, legal_district_code) DO NOTHING;

DELETE FROM member_favorite_region mfr
USING legal_district old_ld
WHERE mfr.legal_district_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');

-- 2) weather_station_mapping: PK가 legal_district_code 단독이라 단순 UPDATE로 충분
UPDATE weather_station_mapping wsm
SET legal_district_code = new_ld.code
FROM legal_district old_ld
JOIN legal_district new_ld ON new_ld.is_active = true
    AND new_ld.name = (
        CASE
            WHEN old_ld.name IN ('광주광역시', '전라남도') THEN '전남광주통합특별시'
            WHEN old_ld.name LIKE '광주광역시 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('광주광역시 ') + 1)
            WHEN old_ld.name LIKE '전라남도 %' THEN '전남광주통합특별시 ' || substring(old_ld.name FROM length('전라남도 ') + 1)
        END
    )
WHERE wsm.legal_district_code = old_ld.code
  AND old_ld.is_active = false
  AND (old_ld.code LIKE '29%' OR old_ld.code LIKE '46%');
