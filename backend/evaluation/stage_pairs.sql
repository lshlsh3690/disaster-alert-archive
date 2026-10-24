-- 검색·생성 단계 집계용: 라벨 쌍마다 두 알림의 시각·최종 이벤트·유형을 뽑는다(stages.py 입력).
-- 실행: docker cp <라벨 CSV> postgres:/tmp/labels.csv 후
--       docker exec -i postgres psql -U postgres -d <DB> -qAt -F'|' < stage_pairs.sql > pairs.txt
CREATE TEMP TABLE l (a bigint, b bigint, label text, s text, t text);
\copy l FROM '/tmp/labels.csv' CSV HEADER
SELECT l.a, l.b, l.label, da.created_at, db.created_at, ma.event_id, mb.event_id, da.disaster_type, db.disaster_type
FROM l
JOIN disaster_alert da ON da.disaster_alert_id = l.a
JOIN disaster_alert db ON db.disaster_alert_id = l.b
LEFT JOIN event_alert_mapping ma ON ma.alert_id = l.a
LEFT JOIN event_alert_mapping mb ON mb.alert_id = l.b;
