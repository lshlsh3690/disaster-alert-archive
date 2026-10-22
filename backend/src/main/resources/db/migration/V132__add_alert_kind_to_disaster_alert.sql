-- 재난문자 성격: INCIDENT(사건) / ADVISORY(특보·안내). NULL = 미분류(분류 실패 또는 과거 데이터)
-- IF NOT EXISTS: 이 파일은 운영 V131 과 번호가 겹쳐 V131 에서 V132 로 옮겼다. 옛 번호로 이미 컬럼을 만든 로컬 DB 에서도 실패하지 않게 한다.
ALTER TABLE disaster_alert ADD COLUMN IF NOT EXISTS alert_kind VARCHAR(16);
