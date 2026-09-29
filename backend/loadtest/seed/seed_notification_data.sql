-- 알림 팬아웃(AlertNotificationService.triggerNotification) 성능 측정용 합성 시드 데이터.
--
-- 운영 데이터가 아니라 순수 합성 데이터다. 로컬 개발 DB 외에는 어디에도 실행하지 말 것.
--
-- seed_stats_data.sql과 달리 "회원 대부분이 같은 지역을 관심지역으로 등록한 상황"을
-- 의도적으로 만든다 — 실제 서비스에서도 인구밀집지역 하나에 관심지역이 쏠릴 수 있고,
-- 이래야 트리거 1번으로 N+1 팬아웃 루프(AlertNotificationService 76~78행, 회원당
-- NotificationPreference 조회 1회 + FcmToken 조회 1회 + UserNotificationLog INSERT 1회 =
-- 순차 쿼리 3N회)에 실제로 부하가 걸린다. 회원마다 서로 다른 지역을 흩어 배정하면 트리거
-- 1번당 매칭되는 회원이 몇 명 안 돼 N+1 자체가 드러나지 않는다.
--
-- 재실행하면 이전에 삽입한 행 위에 새 회원 배치를 이어서 추가한다(멱등 append,
-- seed_stats_data.sql과 동일한 방식) — 회원 이메일은 seed-notify-<n>@example.test 형식.
--
-- 실행:
--   docker exec -i postgres psql -U $POSTGRES_USER -d $POSTGRES_DB \
--     < backend/loadtest/seed/seed_notification_data.sql
-- (.env.dev를 source해서 POSTGRES_USER/POSTGRES_DB를 미리 export해둘 것)
--
-- 측정 방법: 이 스크립트가 마지막에 출력하는 alertId로 AlertNotificationService.
--   triggerNotification()을 실제로 호출해, 애플리케이션 로그의 "회원 팬아웃 완료 -
--   alertId: {alertId}, ..." 줄에서 time=Xms를 읽는다 (@Async라 HTTP 응답시간으로는 못 잰다).
--   실제 Firebase 호출 없이 DB 구간만 격리해서 재려면 `--fcm.dry-run=true`로 기동할 것.
--   (트리거 수단이었던 POST /api/v1/admin/trigger-notification/{alertId}는 E2E 테스트
--   목적을 다해 제거됨 — 다음 라운드 착수 전에 트리거 방식을 다시 정할 것.
--   backend/loadtest/README.md 참고.)

BEGIN;

-- ── 0. 이번 실행에서 채울 범위 결정 ─────────────────────────────
CREATE TEMP TABLE _member_seed_params AS
SELECT
    COALESCE(MAX(SUBSTRING(email FROM 'seed-notify-(\d+)@')::bigint), 0) AS base_offset,
    10000 AS row_count
FROM member
WHERE email LIKE 'seed-notify-%@example.test';

-- ── 1. member (합성 회원) ────────────────────────────────────
CREATE TEMP TABLE _new_members AS
INSERT INTO member (email, password, nickname, role, is_deleted, created_at, updated_at)
SELECT
    'seed-notify-' || (p.base_offset + gs) || '@example.test',
    'synthetic-not-a-real-password-hash',
    'seed-member-' || (p.base_offset + gs),
    'USER',
    false,
    now(),
    now()
FROM _member_seed_params p, generate_series(1, p.row_count) AS gs
RETURNING member_id;

-- ── 2. 핫 지역 선정 ───────────────────────────────────────────
-- 시군구 레벨 코드(뒤 5자리 0, 시도 레벨은 제외) 중 하나를 고정으로 고른다.
-- ORDER BY code로 고정해 재실행해도 항상 같은 지역이 선택된다.
CREATE TEMP TABLE _hot_region AS
SELECT code
FROM legal_district
WHERE is_active = true
  AND code LIKE '%00000'
  AND code NOT LIKE '%00000000'
ORDER BY code
LIMIT 1;

-- ── 3. member_favorite_region ────────────────────────────────
-- 이번에 새로 만든 회원 전원을 핫 지역 관심지역으로 등록 (팬아웃 대상 규모 = row_count 전원).
INSERT INTO member_favorite_region (member_id, legal_district_code, created_at)
SELECT nm.member_id, hr.code, now()
FROM _new_members nm
         CROSS JOIN _hot_region hr
ON CONFLICT (member_id, legal_district_code) DO NOTHING;

-- ── 4. fcm_token ──────────────────────────────────────────────
-- 회원당 토큰 1개. 실제 기기 토큰이 아니므로 --fcm.dry-run=true 없이 기동하면 Firebase가
-- 전부 무효 토큰으로 응답한다 — 이 시드는 dry-run 측정 전용이다.
INSERT INTO fcm_token (member_id, token, device_type, created_at, updated_at)
SELECT member_id, 'synthetic-fcm-token-' || member_id, 'WEB', now(), now()
FROM _new_members
ON CONFLICT (token) DO NOTHING;

-- ── 5. 트리거용 disaster_alert (핫 지역 대상, 팬아웃 규모 그대로 재현) ──
-- disaster_alert_id는 900,000,000 이상 전용 대역만 사용한다. seed_stats_data.sql은
-- 10,000,000대에서 실행마다 60,000씩 늘어나는 append 방식이라 상한이 없는데, 이 스크립트가
-- 쓰는 20,000,000대 근처였다면 이론상 reset 없이 반복 재실행 시(약 167회) 두 대역이
-- 충돌할 수 있었다 — 900,000,000대로 멀리 떨어뜨려 안전 마진을 크게 둔다.
CREATE TEMP TABLE _notify_alert_params AS
SELECT COALESCE(MAX(disaster_alert_id), 900000000) + 1 AS alert_id
FROM disaster_alert
WHERE disaster_alert_id >= 900000000;

CREATE TEMP TABLE _notify_alert AS
INSERT INTO disaster_alert (disaster_alert_id, sn, message, created_at, emergency_level, disaster_type)
SELECT p.alert_id,
       900000000 + p.alert_id,
       '[측정용] 알림 팬아웃 부하테스트 — 핫 지역 전체 발송',
       now(),
       'LEVEL_2',
       '호우'
FROM _notify_alert_params p
RETURNING disaster_alert_id;

INSERT INTO disaster_alert_region (disaster_alert_id, legal_district_code)
SELECT na.disaster_alert_id, hr.code
FROM _notify_alert na
         CROSS JOIN _hot_region hr;

-- ── 6. 결과 요약 ──────────────────────────────────────────────
SELECT 'member (seed-notify- 전체)' AS label, count(*)::text AS value
FROM member WHERE email LIKE 'seed-notify-%@example.test'
UNION ALL
SELECT 'fcm_token (synthetic-fcm-token- 전체)', count(*)::text
FROM fcm_token WHERE token LIKE 'synthetic-fcm-token-%'
UNION ALL
SELECT '핫 지역 code', (SELECT code FROM _hot_region)
UNION ALL
SELECT '핫 지역 관심지역 등록 회원 수', count(*)::text
FROM member_favorite_region WHERE legal_district_code = (SELECT code FROM _hot_region)
UNION ALL
SELECT '트리거용 alertId (triggerNotification() 호출용 — 트리거 수단은 README 참고)', (SELECT disaster_alert_id::text FROM _notify_alert);

COMMIT;
