-- V10__create_user_notification_log.sql
CREATE TABLE IF NOT EXISTS user_notification_log (
    id                BIGSERIAL    PRIMARY KEY,
    member_id         BIGINT       NOT NULL REFERENCES member(member_id) ON DELETE CASCADE,
    alert_id          BIGINT       NOT NULL REFERENCES disaster_alert(disaster_alert_id) ON DELETE CASCADE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'SENT', -- SENT / FAILED
    notification_type VARCHAR(20)  NOT NULL DEFAULT 'PUSH',
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, alert_id)  -- 중복 발송 방지
    );

CREATE INDEX IF NOT EXISTS idx_notification_log_member_id
    ON user_notification_log(member_id);
CREATE INDEX IF NOT EXISTS idx_notification_log_alert_id
    ON user_notification_log(alert_id);