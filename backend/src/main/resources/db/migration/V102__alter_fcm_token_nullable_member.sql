-- fcm_token.member_id를 nullable로 변경하여 비로그인(게스트) FCM 토큰 지원
-- 기존 NOT NULL + FK 제약을 제거하고, nullable FK로 재정의한다.

ALTER TABLE fcm_token
    ALTER COLUMN member_id DROP NOT NULL;

-- 기존 UNIQUE(member_id, device_type)은 member_id가 NULL인 경우 중복을 허용하므로
-- 로그인 회원 대상 unique 제약은 partial index로 대체한다.
ALTER TABLE fcm_token
    DROP CONSTRAINT IF EXISTS fcm_token_member_id_device_type_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_fcm_token_member_device
    ON fcm_token (member_id, device_type)
    WHERE member_id IS NOT NULL;
