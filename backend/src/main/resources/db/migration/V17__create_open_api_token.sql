CREATE TABLE IF NOT EXISTS open_api_token (
    open_api_token_id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP,
    CONSTRAINT fk_open_api_token_member FOREIGN KEY (member_id) REFERENCES member (member_id)
);

CREATE INDEX IF NOT EXISTS idx_open_api_token_member_id ON open_api_token (member_id);
CREATE INDEX IF NOT EXISTS idx_open_api_token_expires_at ON open_api_token (expires_at);
