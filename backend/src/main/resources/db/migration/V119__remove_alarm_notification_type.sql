-- NotificationType.ALARM 제거: enum 에 없는 값이 남으면 조회 시 역직렬화 예외가 나므로 PUSH 로 이관 (멱등)
UPDATE notification_preference SET notification_type = 'PUSH' WHERE notification_type = 'ALARM';
