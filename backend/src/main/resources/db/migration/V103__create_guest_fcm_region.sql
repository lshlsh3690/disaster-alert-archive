-- 비로그인(게스트) FCM 토큰과 관심지역 매핑 테이블
-- FCM 토큰 1개당 최대 5개 지역 코드를 저장한다.
-- 로그인 시 member_favorite_region으로 이관되고 이 레코드는 삭제된다.

CREATE TABLE IF NOT EXISTS guest_fcm_region (
    id                  BIGSERIAL    PRIMARY KEY,
    fcm_token           TEXT         NOT NULL,
    legal_district_code VARCHAR(10)  NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_guest_fcm_region
        UNIQUE (fcm_token, legal_district_code),
    CONSTRAINT fk_guest_fcm_region_legal_district
        FOREIGN KEY (legal_district_code) REFERENCES legal_district (code)
);

-- 알림 발송 시 "지역 코드 → 토큰" 역방향 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_guest_fcm_region_legal_district_code
    ON guest_fcm_region (legal_district_code);

-- 토큰 기준 조회용 인덱스
CREATE INDEX IF NOT EXISTS idx_guest_fcm_region_fcm_token
    ON guest_fcm_region (fcm_token);
