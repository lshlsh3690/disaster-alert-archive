-- 매핑 행이 어떤 방식으로 이벤트에 들어왔는지 표식 (감사/튜닝용, 특히 LLM 오판 검수).
-- SEED=이벤트 첫 알림 / EMBEDDING=코사인 머지 / BROADCAST=시도+유형·전국 머지 / LLM=cross-region 판정.
-- 기존 행은 NULL → recluster + cross-region 백필 패스로 재채움.
ALTER TABLE event_alert_mapping ADD COLUMN merge_method VARCHAR(16);
