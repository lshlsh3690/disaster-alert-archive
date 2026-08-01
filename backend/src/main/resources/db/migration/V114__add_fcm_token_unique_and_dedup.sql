-- fcm_token.token 전역 UNIQUE 제약 추가 (+ 기존 중복 행 정리)
--
-- 배경: V9 의 UNIQUE(member_id, device_type) 는 V102 에서 member_id IS NOT NULL 부분 유니크
-- 인덱스로 대체됐다. 그 결과 게스트 토큰(member_id = NULL)에는 어떤 유니크 제약도 걸리지 않아,
-- 프론트가 신규 토큰 발급 직후 두 경로(retryGetToken / guestRegions useEffect)에서 동시에
-- POST /api/v1/fcm-token/guest 를 호출할 때 findByToken → save 가 원자적이지 않아
-- 동일 토큰이 여러 행으로 저장됐다. 이후 그 토큰으로 등록/삭제/연결 시
-- findByToken(단건 Optional)이 NonUniqueResultException 을 던져 게스트 FCM 등록이 500 으로 실패했다.
--
-- 물리적 FCM 토큰은 (기기 + 앱)당 유일하므로 token 은 전역적으로 1행이어야 한다.

-- 1) 토큰별 중복 행 제거: 회원 연결 행(member_id NOT NULL)을 우선 보존하고,
--    그다음 최신(id 큰) 행을 보존한다. guest_fcm_region 은 fcm_token.id 가 아니라
--    토큰 문자열로 연결되므로 이 삭제의 영향을 받지 않는다.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY token
               ORDER BY (member_id IS NOT NULL) DESC, id DESC
           ) AS rn
    FROM fcm_token
)
DELETE FROM fcm_token
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 2) token 전역 UNIQUE
CREATE UNIQUE INDEX IF NOT EXISTS uq_fcm_token_token
    ON fcm_token (token);
