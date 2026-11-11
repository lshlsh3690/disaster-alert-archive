-- event_alert_mapping 표를 없애고 그 내용을 disaster_alert 로 옮긴다.
--
-- 한 알림은 한 이벤트에만 속한다(uq_event_alert_mapping_alert). 이벤트(1) ↔ 알림(N) 관계라 연결 표가
-- 필요 없고, 엔티티도 DisasterAlert 가 DisasterEvent 를 @ManyToOne 으로 직접 가리킨다(2026-11-11).
-- 매핑 표에만 있던 순번·합류 유사도·합류 방식·합류 시각은 event_ 접두사를 붙여 그대로 옮긴다.
--
-- 이벤트가 지워지면 event_id 만 NULL 이 된다(ON DELETE SET NULL). 예전에는 매핑 행이 통째로 지워졌으므로,
-- 이벤트를 지우는 작업(백필 재클러스터링 등)은 event_ 컬럼 다섯 개를 먼저 NULL 로 비운다.

ALTER TABLE disaster_alert
    ADD COLUMN event_id               BIGINT REFERENCES disaster_events (id) ON DELETE SET NULL,
    ADD COLUMN event_sequence_no      INT,
    ADD COLUMN event_similarity_score DOUBLE PRECISION,
    ADD COLUMN event_merge_method     VARCHAR(16),
    ADD COLUMN event_added_at         TIMESTAMP;

UPDATE disaster_alert a
SET event_id               = m.event_id,
    event_sequence_no      = m.sequence_no,
    event_similarity_score = m.similarity_score,
    event_merge_method     = NULLIF(m.merge_method, 'ADVISORY'),   -- MergeMethod 에서 지운 값(V136 이 먼저 지우지만 방어)
    event_added_at         = m.added_at
FROM event_alert_mapping m
WHERE m.alert_id = a.disaster_alert_id;

CREATE INDEX idx_disaster_alert_event ON disaster_alert (event_id);

DROP TABLE event_alert_mapping;
