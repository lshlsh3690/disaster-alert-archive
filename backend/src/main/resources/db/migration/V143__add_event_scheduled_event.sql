-- 전국 예정 행사(수능·민방위훈련) 이벤트의 "행사 종류" 키 — disaster_events.scheduled_event
--
-- 같은 행사의 알림을 한 이벤트로 묶는 기준을 이벤트 제목 일치에서 이 컬럼으로 옮긴다. 제목은 화면에 보이는 값이라
-- 손으로 고치거나 형식을 바꾸면(운영 35605 를 "2026 수능"으로 바꾼 일) 같은 행사를 못 찾기 때문이다.
-- 행사 판단은 "같은 종류 + 마지막 알림 후 72시간"이라 연도는 키에 넣지 않는다(72시간이면 해마다 저절로 갈린다).
--
-- scheduled_event : NationalScheduledEvent enum 이름(SUNEUNG, CIVIL_DEFENSE_DRILL). 행사 이벤트가 아니면 NULL
--                   (일반 전국 광역 병합이 NULL 인 이벤트만 후보로 삼아 행사 이벤트를 후보에서 뺀다)
ALTER TABLE disaster_events ADD COLUMN scheduled_event VARCHAR(32);

-- 이미 만들어진 행사 이벤트 채우기 — 행사 경로가 병합한 알림(event_merge_method = 'NATIONAL_EVENT')이 든 이벤트 중에서
-- 제목 끝이 행사 이름인 것만 종류를 정한다. 제목을 "2026 수능"처럼 손으로 바꿨어도 끝이 "수능"이면 잡힌다.
-- 운영에는 35605("2026 수능") 하나뿐이고, 행사 경로가 없던 DB 에서는 일치하는 행이 없어 아무것도 바뀌지 않는다.
UPDATE disaster_events e
SET scheduled_event = CASE
        WHEN e.event_title LIKE '%수능' THEN 'SUNEUNG'
        WHEN e.event_title LIKE '%민방위훈련' THEN 'CIVIL_DEFENSE_DRILL'
    END
WHERE e.scheduled_event IS NULL
  AND (e.event_title LIKE '%수능' OR e.event_title LIKE '%민방위훈련')
  AND EXISTS (SELECT 1 FROM disaster_alert a
              WHERE a.event_id = e.id AND a.event_merge_method = 'NATIONAL_EVENT');
