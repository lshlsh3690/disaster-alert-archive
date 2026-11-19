-- 기존 재난문자에 발신 기관을 채운다(specs/007 R11). 스키마는 V140.
--
-- 표기 추출은 Java SenderLabel 과 같은 규칙이다: 본문 끝 대괄호 우선, 없으면 맨 앞 대괄호.
--   끝:   \[([^][]+)\]\s*$   (Java: \[([^\[\]]+)]\s*$)
--   맨 앞: ^\s*\[([^][]+)\]   (Java: ^\s*\[([^\[\]]+)])
-- 2026-11-19 운영 사본 62,554건에서 이 SQL 과 SenderLabel.extract 결과가 한 건도 다르지 않음을 확인했다
-- (표기 있음 62,545 · 없음 9 · 서로 다른 표기 709 · 최대 23자). 규칙을 바꾸면 두 곳을 같이 바꾸고 다시 대조할 것.
--
-- 처음 보는 표기마다 기관 1개를 만든다(이름 = 표기 그대로, 정규화는 나중 일). 이미 agency_alias 에 있는 표기는
-- 그 기관을 그대로 쓴다. 100자를 넘는 표기는 컬럼 길이(V140)를 넘으므로 건너뛴다 — 수집 경로(AgencyService)와 같은 처리.

-- 1) 아직 등록되지 않은 표기마다 기관과 표기를 만든다. 표기가 서로 다르므로 RETURNING 의 name 이 곧 표기다
WITH extracted AS (
    SELECT COALESCE(
               substring(message FROM '\[([^][]+)\]\s*$'),
               substring(message FROM '^\s*\[([^][]+)\]')
           ) AS label
    FROM disaster_alert
),
new_labels AS (
    SELECT DISTINCT e.label
    FROM extracted e
    WHERE e.label IS NOT NULL
      AND length(e.label) <= 100
      AND NOT EXISTS (SELECT 1 FROM agency_alias al WHERE al.label = e.label)
),
created AS (
    INSERT INTO agency (name, created_at)
    SELECT label, now() FROM new_labels
    RETURNING id, name
)
INSERT INTO agency_alias (label, agency_id, created_at)
SELECT name, id, now() FROM created;

-- 2) 표기가 가리키는 기관을 알림에 채운다. 이미 기관이 있는 알림(V140 이후 수집분)은 건드리지 않는다
--    표기를 알림마다 한 번만 뽑아 두고(labeled) 조인한다 — 조인 조건 안에 정규식을 두면 플래너가 알림 × 표기 수만큼
--    정규식을 돌려 운영 사본에서 90초가 걸렸다(이 형태는 수 초).
WITH labeled AS (
    SELECT disaster_alert_id,
           COALESCE(
               substring(message FROM '\[([^][]+)\]\s*$'),
               substring(message FROM '^\s*\[([^][]+)\]')
           ) AS label
    FROM disaster_alert
    WHERE agency_id IS NULL
)
UPDATE disaster_alert a
SET agency_id = al.agency_id
FROM labeled l
JOIN agency_alias al ON al.label = l.label
WHERE a.disaster_alert_id = l.disaster_alert_id;
