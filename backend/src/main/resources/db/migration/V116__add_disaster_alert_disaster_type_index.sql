-- disaster_type에 인덱스가 없어 /stats의 countByType(유형별 집계)이 매번 disaster_alert
-- 전체를 Seq Scan한 뒤 디스크에 스필하는 정렬을 거쳤다 (6만 건 기준 EXPLAIN ANALYZE로
-- 162.9ms 확인). GROUP BY disaster_type이 Index Scan으로 이미 정렬된 순서를 활용하도록
-- 인덱스를 추가한다.
CREATE INDEX IF NOT EXISTS idx_disaster_alert_disaster_type
    ON disaster_alert (disaster_type);
