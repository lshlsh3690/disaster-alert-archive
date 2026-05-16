CREATE TABLE IF NOT EXISTS notification_preference (
                                                       id                BIGSERIAL PRIMARY KEY,
                                                       member_id         BIGINT NOT NULL UNIQUE
                                                       REFERENCES member(member_id) ON DELETE CASCADE,
    notification_type VARCHAR(20) NOT NULL DEFAULT 'PUSH',
    min_risk_score    INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_notification_preference_member_id
    ON notification_preference(member_id);