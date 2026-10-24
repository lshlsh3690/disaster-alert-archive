-- 클러스터링 평가: 과소병합율 · 과병합율
--
-- 하는 일: 사람이 라벨한 알림 쌍마다 "두 알림이 같은 이벤트에 들어 있는가"를 DB 에서 확인한다.
-- 그게 전부다. LLM 판사도, 재현 로직도 없다.
--
-- 사람 라벨 \ 시스템 | 같은 이벤트   | 다른 이벤트
-- 같은 사건(SAME)     | 맞게 묶음     | 놓침
-- 다른 사건(DIFF)     | 잘못 묶음     | (세지 않음)
--
-- 과소병합율 = 놓침 ÷ (맞게 묶음 + 놓침)        → 같은 사건인데 못 묶은 비율
-- 과병합율   = 잘못 묶음 ÷ (맞게 묶음 + 잘못 묶음) → 묶은 것 중 틀린 비율
--
-- 실행: eval.sh <DB 이름> <라벨 CSV 경로>   (eval.sh 가 CSV 를 컨테이너의 /tmp/labels.csv 로 복사한다)

-- 1) 라벨 CSV 를 임시 테이블에 넣는다. 세션이 끝나면 사라진다(DB 에 아무것도 남기지 않음).
CREATE TEMP TABLE label (a bigint, b bigint, label text, stratum text, sub_type text);
\copy label FROM '/tmp/labels.csv' CSV HEADER

-- 2) 쌍마다 두 알림의 이벤트 번호를 붙인다. 이벤트에 안 들어간 알림은 event 가 NULL 이다.
CREATE TEMP VIEW pair AS
SELECT l.*, ma.event_id AS event_a, mb.event_id AS event_b
FROM label l
LEFT JOIN event_alert_mapping ma ON ma.alert_id = l.a
LEFT JOIN event_alert_mapping mb ON mb.alert_id = l.b;

-- 3) 개수 세기
\echo '== 개수 =='
SELECT
  count(*) FILTER (WHERE label = 'SAME' AND event_a = event_b)  AS 맞게_묶음,
  count(*) FILTER (WHERE label = 'SAME' AND event_a <> event_b) AS 놓침,
  count(*) FILTER (WHERE label = 'DIFF' AND event_a = event_b)  AS 잘못_묶음,
  count(*) FILTER (WHERE label = 'DIFF' AND event_a <> event_b) AS 맞게_분리,
  count(*) FILTER (WHERE label = 'UNSURE')                       AS 제외_모르겠음,
  count(*) FILTER (WHERE label <> 'UNSURE' AND (event_a IS NULL OR event_b IS NULL)) AS 제외_미배정,
  count(*) AS 전체
FROM pair;

-- 4) 두 비율 (분모가 0 이면 NULL)
\echo '== 비율 =='
SELECT
  round(100.0 * count(*) FILTER (WHERE label = 'SAME' AND event_a <> event_b)
        / nullif(count(*) FILTER (WHERE label = 'SAME' AND event_a IS NOT NULL AND event_b IS NOT NULL), 0), 1) AS 과소병합율_퍼센트,
  round(100.0 * count(*) FILTER (WHERE label = 'DIFF' AND event_a = event_b)
        / nullif(count(*) FILTER (WHERE event_a = event_b AND label IN ('SAME', 'DIFF')), 0), 1) AS 과병합율_퍼센트
FROM pair;

-- 5) 틀린 쌍 목록 — 사람이 눈으로 읽고 묶음을 나누는 용도
\echo '== 틀린 쌍 =='
SELECT CASE WHEN p.label = 'SAME' THEN '놓침' ELSE '잘못묶음' END AS 구분,
       da.disaster_type AS 유형,
       round(abs(extract(epoch FROM db.created_at - da.created_at)) / 3600) AS 간격_시간,
       round((da.embedding <=> db.embedding)::numeric, 3) AS 본문거리,
       p.a, p.b,
       left(regexp_replace(da.message, '\s+', ' ', 'g'), 70) AS 문자a,
       left(regexp_replace(db.message, '\s+', ' ', 'g'), 70) AS 문자b
FROM pair p
JOIN disaster_alert da ON da.disaster_alert_id = p.a
JOIN disaster_alert db ON db.disaster_alert_id = p.b
WHERE (p.label = 'SAME' AND p.event_a <> p.event_b)
   OR (p.label = 'DIFF' AND p.event_a = p.event_b)
ORDER BY 구분, 유형;
