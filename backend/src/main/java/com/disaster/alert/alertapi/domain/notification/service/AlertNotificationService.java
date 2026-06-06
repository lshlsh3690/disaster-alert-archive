package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.repository.MemberFavoriteRegionRepository;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    private final MemberFavoriteRegionRepository favoriteRegionRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final GuestFcmRegionRepository guestFcmRegionRepository;
    private final UserNotificationLogRepository notificationLogRepository;
    private final FcmSendService fcmSendService;
    private final DisasterAlertRepository disasterAlertRepository;

    @Async
    @Transactional
    public void triggerNotification(Long alertId) {
        try {
            // 1. 재난문자 조회
            DisasterAlert alert = disasterAlertRepository.findById(alertId)
                    .orElse(null);
            if (alert == null) return;

            // 2. 재난문자의 지역코드 목록 추출
            List<String> regionCodes = alert.getDisasterAlertRegions()
                    .stream()
                    .map(r -> r.getId().getDistrictCode())
                    .filter(code -> code != null && !code.isBlank())
                    .distinct()
                    .toList();

            if (regionCodes.isEmpty()) return;

            String title = "[재난문자] " + alert.getDisasterType();
            String body = alert.getMessage();

            // 3. 해당 지역들을 관심지역으로 등록한 사용자 조회
            // 시도 전체 등록 사용자를 위해 시도 레벨 코드도 포함 (예: "2900300000" → "2900000000")
            List<String> sidoCodes = regionCodes.stream()
                    .filter(code -> code.length() == 10)
                    .map(code -> code.substring(0, 2) + "00000000")
                    .distinct()
                    .toList();
            List<String> allCodesToSearch = Stream.concat(regionCodes.stream(), sidoCodes.stream())
                    .distinct()
                    .toList();

            List<Long> memberIds = favoriteRegionRepository
                    .findByIdLegalDistrictCodeIn(allCodesToSearch)
                    .stream()
                    .map(r -> r.getId().getMemberId())
                    .distinct()
                    .toList();

            if (!memberIds.isEmpty()) {
                log.info("회원 알림 발송 대상: {}명, alertId: {}", memberIds.size(), alertId);
                for (Long memberId : memberIds) {
                    sendToMember(memberId, alertId, title, body);
                }
            }

            // 게스트 토큰 발송
            sendToGuestTokens(allCodesToSearch, title, body);

        } catch (Exception e) {
            log.error("알림 트리거 실패 - alertId: {}, error: {}", alertId, e.getMessage());
        }
    }

    private void sendToGuestTokens(List<String> regionCodes, String title, String body) {
        try {
            List<String> tokens = guestFcmRegionRepository
                    .findAllByLegalDistrictCodeIn(regionCodes)
                    .stream()
                    .map(r -> r.getFcmToken())
                    .distinct()
                    .toList();

            if (tokens.isEmpty()) return;

            log.info("게스트 알림 발송 대상: {}개 토큰", tokens.size());

            if (tokens.size() == 1) {
                fcmSendService.sendToToken(tokens.get(0), title, body,
                        NotificationType.PUSH.name(), null);
            } else {
                fcmSendService.sendToTokens(tokens, title, body,
                        NotificationType.PUSH.name(), null);
            }
        } catch (Exception e) {
            log.error("게스트 알림 발송 실패: {}", e.getMessage());
        }
    }

    private void sendToMember(Long memberId, Long alertId, String title, String body) {
        try {
            // 중복 발송 방지
            if (notificationLogRepository.existsByMemberIdAndAlertId(memberId, alertId)) {
                return;
            }

            // 알림 타입 조회
            String notificationType = preferenceRepository
                    .findByMemberId(memberId)
                    .map(p -> p.getNotificationType().name())
                    .orElse(NotificationType.PUSH.name());

            // NONE이면 발송 안 함
            if (NotificationType.NONE.name().equals(notificationType)) return;

            // FCM 토큰 조회
            List<String> tokens = fcmTokenRepository
                    .findAllByMemberId(memberId)
                    .stream()
                    .map(t -> t.getToken())
                    .toList();

            if (tokens.isEmpty()) return;

            // FCM 발송
            boolean sent = tokens.size() == 1
                    ? fcmSendService.sendToToken(tokens.get(0), title, body,
                    notificationType, String.valueOf(alertId))
                    : fcmSendService.sendToTokens(tokens, title, body,
                    notificationType, String.valueOf(alertId)) != null;

            // 발송 이력 저장
            notificationLogRepository.save(
                    UserNotificationLog.builder()
                            .memberId(memberId)
                            .alertId(alertId)
                            .status(sent ? "SENT" : "FAILED")
                            .notificationType(notificationType)
                            .build()
            );

        } catch (Exception e) {
            log.error("개별 알림 발송 실패 - memberId: {}, error: {}", memberId, e.getMessage());
        }
    }
}