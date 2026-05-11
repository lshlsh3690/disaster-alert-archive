CREATE TABLE IF NOT EXISTS notification_log (
    id                  BIGSERIAL    NOT NULL,
    member_id           BIGINT       NOT NULL,
    disaster_alert_id   BIGINT       NOT NULL,
    is_read             BOOLEAN      NOT NULL DEFAULT FALSE,
    sent_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_notification_log
        PRIMARY KEY (id),
    CONSTRAINT fk_nl_member
        FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_nl_disaster_alert
        FOREIGN KEY (disaster_alert_id) REFERENCES disaster_alert (disaster_alert_id),
    CONSTRAINT uq_nl_member_alert
        UNIQUE (member_id, disaster_alert_id)
);

-- 사용자 알림함 최신순 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_nl_member_sent_at
    ON notification_log (member_id, sent_at DESC);
