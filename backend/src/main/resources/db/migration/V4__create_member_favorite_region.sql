CREATE TABLE IF NOT EXISTS member_favorite_region (
    member_id           BIGINT       NOT NULL,
    legal_district_code VARCHAR(10)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_member_favorite_region
        PRIMARY KEY (member_id, legal_district_code),
    CONSTRAINT fk_mfr_member
        FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_mfr_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code)
);

-- 알림 발송 시 "지역 코드 → 사용자" 역방향 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_mfr_legal_district_code
    ON member_favorite_region (legal_district_code);
