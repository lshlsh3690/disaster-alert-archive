-- deviceType = 'WEB' FCM 토큰 일괄 삭제 (웹 푸시 모바일 전용 전환, 2026-10)
--
-- 푸시 알림을 휴대폰·태블릿 전용으로 바꾸며 프론트는 모바일 브라우저를 'MOBILE_WEB'(TWA 는 'ANDROID')으로
-- 등록하고 데스크톱에서는 등록하지 않는다. 전환 전 값 'WEB' 은 데스크톱과 모바일 브라우저가 섞여 있어
-- 구분할 수 없으므로 전부 지운다. 유예 기간 없이 지우기로 한 것은 사용자 결정(2026-10-26, 실사용자 없음).
-- 모바일에서 알림을 이미 허용한 사용자는 다음에 앱을 열 때 권한이 granted 라 토큰이 'MOBILE_WEB' 으로 자동
-- 재등록된다(useNotificationPermission). 그 전까지는 알림을 받지 못한다.
--
-- guest_fcm_region 은 fcm_token.id 가 아니라 토큰 문자열로 연결되고 외래 키가 없으므로 먼저 지운다.
-- 'ANDROID'(TWA)·'IOS' 토큰은 건드리지 않는다.

DELETE FROM guest_fcm_region
WHERE fcm_token IN (SELECT token FROM fcm_token WHERE device_type = 'WEB');

DELETE FROM fcm_token
WHERE device_type = 'WEB';
