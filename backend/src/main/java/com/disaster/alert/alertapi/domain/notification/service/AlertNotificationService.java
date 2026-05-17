package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final UserNotificationLogRepository notificationLogRepository;
    private final FcmSendService fcmSendService;

    // 재난문자 수신 시 관심지역 매칭 사용자에게 FCM 발송
    @Transactional
    public void sendAlertToMatchedUsers(Long alertId, String regionCode,
                                        String title, String body) {
        // 1. 해당 지역을 관심지역으로 등록한 사용자 ID 목록 조회
        List<Long> memberIds = findMembersByRegionCode(regionCode);
        if (memberIds.isEmpty()) return;

        log.info("알림 발송 대상: {}명, alertId: {}", memberIds.size(), alertId);

        for (Long memberId : memberIds) {
            try {
                // 2. 중복 발송 방지 체크
                if (notificationLogRepository.existsByMemberIdAndAlertId(memberId, alertId)) {
                    log.debug("중복 알림 스킵 - memberId: {}, alertId: {}", memberId, alertId);
                    continue;
                }

                // 3. 알림 타입 조회 (없으면 기본값 PUSH)
                String notificationType = preferenceRepository
                        .findByMemberId(memberId)
                        .map(p -> p.getNotificationType().name())
                        .orElse(NotificationType.PUSH.name());

                // 4. NONE이면 발송 안 함
                if (NotificationType.NONE.name().equals(notificationType)) {
                    log.debug("알림 비활성화 사용자 스킵 - memberId: {}", memberId);
                    continue;
                }

                // 5. FCM 토큰 목록 조회
                List<String> tokens = fcmTokenRepository
                        .findAllByMemberId(memberId)
                        .stream()
                        .map(t -> t.getToken())
                        .toList();

                if (tokens.isEmpty()) continue;

                // 6. FCM 발송
                boolean sent = false;
                if (tokens.size() == 1) {
                    sent = fcmSendService.sendToToken(
                            tokens.get(0), title, body,
                            notificationType, String.valueOf(alertId));
                } else {
                    var response = fcmSendService.sendToTokens(
                            tokens, title, body,
                            notificationType, String.valueOf(alertId));
                    sent = response != null && response.getSuccessCount() > 0;
                }

                // 7. 발송 이력 저장
                notificationLogRepository.save(
                        UserNotificationLog.builder()
                                .memberId(memberId)
                                .alertId(alertId)
                                .status(sent ? "SENT" : "FAILED")
                                .notificationType(notificationType)
                                .build()
                );

            } catch (Exception e) {
                log.error("알림 발송 실패 - memberId: {}, error: {}", memberId, e.getMessage());
            }
        }
    }

    // 관심지역 코드로 사용자 ID 조회
    private List<Long> findMembersByRegionCode(String regionCode) {
        // user_alert_regions 테이블에서 조회
        // 기존 쿼리 활용
        return List.of(); // 아래에서 Repository 주입 후 구현
    }
}