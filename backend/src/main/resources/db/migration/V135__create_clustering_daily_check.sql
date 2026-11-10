-- 클러스터링 일일 구조 검사 결과 (하루 1줄)
--
-- 매일 전날(KST) 사건(alert_kind = 'INCIDENT') 알림 중 이벤트에 들어가지 못한 건수를 센 결과다. 운영에 미배정
-- 사건 알림이 551건 쌓여 있었는데 아무도 몰랐던 일을 막으려고, 사람이 건별로 보지 않아도 숫자로 알 수 있게 남긴다.
--
-- check_date : 검사한 날짜 (PK). 같은 날짜를 다시 검사하면 이 줄을 갱신해 하루 1줄을 유지한다.
-- incident_count : 그 날짜(created_at 이 KST 00:00 이상 다음날 00:00 미만)에 생성된 INCIDENT 알림 수
-- unassigned_count : 그 중 event_alert_mapping 에 없는(어느 이벤트에도 속하지 않은) 알림 수
-- no_embedding_count : 그 중 embedding 이 NULL 인 알림 수 (임베딩 실패가 미배정의 원인인지 가르는 신호)
-- checked_at : 검사를 실행한 시각
CREATE TABLE clustering_daily_check (
    check_date         DATE      PRIMARY KEY,
    incident_count     INT       NOT NULL,
    unassigned_count   INT       NOT NULL,
    no_embedding_count INT       NOT NULL,
    checked_at         TIMESTAMP NOT NULL
);
