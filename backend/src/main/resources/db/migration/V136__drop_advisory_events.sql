-- 안내 이벤트(is_advisory=true)와 is_advisory 컬럼을 지운다.
--
-- 안내 분리 경로는 2026-10-17(d314cb3d)에 삭제됐고, 그 뒤로는 비사건(alert_kind=ADVISORY) 알림을 아예
-- 클러스터링하지 않아 is_advisory=true 이벤트를 새로 만드는 경로가 없다. 남은 것은 그 전에 만들어진
-- 안내 이벤트뿐이다(2026-11-11 운영 627개, 모두 지역 한정·지속형 유형, 마지막 알림 2026-10-21).
-- 이들은 후보 검색의 최장 기간(168h)을 지나 병합 대상이 될 수 없어 is_advisory=false 필터가 실제로
-- 걸러내는 것이 없었다.
--
-- 함께 사라지는 것: event_alert_mapping(ON DELETE CASCADE, merge_method='ADVISORY' 행 전부가 여기 속함),
-- disaster_event_translation(CASCADE). 안내 이벤트에만 들어 있던 알림은 미배정으로 남는다
-- (운영 기준 ADVISORY 5,491건, INCIDENT 산불 177건).
--
-- region_risk_index 는 외래 키에 CASCADE 가 없어 참조를 먼저 비운다(위험도 계산은 2026-11-05 삭제, 런타임 미사용).

UPDATE region_risk_index
SET top_event_id = NULL
WHERE top_event_id IN (SELECT id FROM disaster_events WHERE is_advisory);

UPDATE region_risk_index
SET source_top_event_id = NULL
WHERE source_top_event_id IN (SELECT id FROM disaster_events WHERE is_advisory);

DELETE FROM disaster_events WHERE is_advisory;

ALTER TABLE disaster_events DROP COLUMN is_advisory;
