-- V115__drop_user_notification_log_dedup_unique.sql
-- 회원 알림 재발송 시 중복 발송 방지 제약을 제거한다 (테스트/재발송 시나리오에서
-- 동일 alertId로 재발송이 막히던 문제 해소).
ALTER TABLE user_notification_log DROP CONSTRAINT IF EXISTS user_notification_log_member_id_alert_id_key;