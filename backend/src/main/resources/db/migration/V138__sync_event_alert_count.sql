-- disaster_events.alert_count 를 실제로 그 이벤트에 든 알림 수로 맞춘다.
--
-- 2026-11-11 부터 병합 순번(disaster_alert.event_sequence_no)을 count 쿼리가 아니라 alert_count + 1 로 매긴다
-- (DisasterEvent.addAlert). alert_count 가 실제 수와 다르면 순번이 비거나 겹치므로 한 번 맞춰 둔다.
-- 2026-11-11 운영 확인: 35,513개 중 1개(이벤트 30936, alert_count 11 / 실제 10)만 달랐다.
-- V137(매핑을 disaster_alert 로 이동) 뒤에 실행돼야 한다.

UPDATE disaster_events e
SET alert_count = c.n
FROM (SELECT event_id, COUNT(*) AS n
      FROM disaster_alert
      WHERE event_id IS NOT NULL
      GROUP BY event_id) c
WHERE c.event_id = e.id
  AND e.alert_count <> c.n;
