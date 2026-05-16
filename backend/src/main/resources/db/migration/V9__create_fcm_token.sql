CREATE TABLE IF NOT EXISTS fcm_token (
                                         id          BIGSERIAL    PRIMARY KEY,
                                         member_id   BIGINT       NOT NULL
                                         REFERENCES member(member_id) ON DELETE CASCADE,
    token       TEXT         NOT NULL,
    device_type VARCHAR(20)  NOT NULL DEFAULT 'WEB',
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (member_id, device_type)  -- 기기 타입별 1개 토큰만 유지
    );

CREATE INDEX IF NOT EXISTS idx_fcm_token_member_id
    ON fcm_token(member_id);