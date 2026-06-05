-- 이벤트 제목 다국어 번역 캐시 (disaster_alert_translation 패턴 복제).
-- 조회 시 lazy 번역(DeepL) → 저장. (event_id, language_code) 당 1행.
CREATE TABLE disaster_event_translation (
    disaster_event_id BIGINT      NOT NULL REFERENCES disaster_events (id) ON DELETE CASCADE,
    language_code     VARCHAR(10) NOT NULL,
    translated_title  TEXT        NOT NULL,
    translated_at     TIMESTAMP   NOT NULL,
    PRIMARY KEY (disaster_event_id, language_code)
);
