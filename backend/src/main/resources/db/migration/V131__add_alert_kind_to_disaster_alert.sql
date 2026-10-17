-- 재난문자 성격: INCIDENT(사건) / ADVISORY(특보·안내). NULL = 미분류(분류 실패 또는 과거 데이터)
ALTER TABLE disaster_alert ADD COLUMN alert_kind VARCHAR(16);
