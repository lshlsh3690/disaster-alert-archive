package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.repository.MemberFavoriteRegionRepository;
import com.disaster.alert.alertapi.domain.notification.dto.MemberNotificationType;
import com.disaster.alert.alertapi.domain.notification.dto.MemberToken;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.GuestFcmRegionRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertNotificationService {

    // UserNotificationLog가 GenerationType.IDENTITY라 INSERT를 배치로 못 묶는다 — 대신 이 건수마다
    // 영속성 컨텍스트를 비워 회원 1만 명 팬아웃 내내 managed 엔티티가 계속 쌓이는 걸 막는다
    // (실측: 루프 진행에 따라 건당 31ms→93ms로 3배 증가). 500은 이번 벌크 조회 리팩터링과 함께
    // 정한 값으로, 튜닝된 값이 아니라 "너무 자주도, 너무 뜸하지도 않은" 보수적인 출발점이다.
    private static final int FLUSH_INTERVAL = 500;

    private final MemberFavoriteRegionRepository favoriteRegionRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final GuestFcmRegionRepository guestFcmRegionRepository;
    private final UserNotificationLogRepository notificationLogRepository;
    private final FcmSendService fcmSendService;
    private final DisasterAlertRepository disasterAlertRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Async
    @Transactional
    public void triggerNotification(Long alertId) {
        // @Async라 HTTP 응답시간으로 못 재고, ThreadLocalLogTrace AOP도 @Async 어드바이저와의
        // 순서가 둘 다 @Order 미지정이라 정적으로 불확실해 못 믿는다 — 직접 재서 로그로 남긴다
        // (backend/loadtest/가 이 time=Xms를 파싱).
        long startNanos = System.nanoTime();
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
            List<String> allCodesToSearch = withSidoCodes(regionCodes);

            List<Long> memberIds = favoriteRegionRepository
                    .findByIdLegalDistrictCodeIn(allCodesToSearch)
                    .stream()
                    .map(r -> r.getId().getMemberId())
                    .distinct()
                    .toList();

            if (!memberIds.isEmpty()) {
                sendToMembers(memberIds, alertId, title, body);
            }

            // 게스트 토큰 발송
            sendToGuestTokens(allCodesToSearch, alertId, title, body);

            long totalMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("알림 트리거 완료 - alertId: {}, time={}ms", alertId, totalMs);

        } catch (Exception e) {
            log.error("알림 트리거 실패 - alertId: {}, error: {}", alertId, e.getMessage());
        }
    }

    // 시도 전체 등록 사용자를 위해 시도 레벨 코드도 포함 (예: "2900300000" → "2900000000")
    private List<String> withSidoCodes(List<String> regionCodes) {
        List<String> sidoCodes = regionCodes.stream()
                .filter(code -> code.length() == 10)
                .map(code -> code.substring(0, 2) + "00000000")
                .distinct()
                .toList();
        return Stream.concat(regionCodes.stream(), sidoCodes.stream())
                .distinct()
                .toList();
    }

    private void sendToMembers(List<Long> memberIds, Long alertId, String title, String body) {
        log.info("회원 알림 발송 대상: {}명, alertId: {}", memberIds.size(), alertId);
        long fanoutStartNanos = System.nanoTime();

        // N+1 방지: 루프 진입 전에 회원 전체의 알림설정/토큰을 각각 1번의 IN 쿼리로 미리 조회한다.
        // 이 두 조회는 sendToMember()의 try/catch 밖이라, 실패하면 회원 1명이 아니라 대상
        // 전원의 발송이 스킵되고 triggerNotification()의 바깥 catch로 떨어진다 — 예전
        // N+1 구조(회원별 조회, 회원 1명 실패가 나머지에 안 번짐)와 실패 모드가 다르다.
        // 의도적으로 받아들인 트레이드오프다: 두 쿼리가 실패하는 상황은 거의 항상 DB 자체
        // 장애라 어차피 회원별로 쪼개 불러도 전원 실패할 가능성이 높고, 그럴 땐 에러 로그
        // 1만 줄보다 한 줄로 명확히 실패를 알리는 쪽이 낫다고 판단했다.
        Map<Long, String> typeByMember = preferenceRepository.findNotificationTypesByMemberIdIn(memberIds)
                .stream()
                .collect(Collectors.toMap(
                        MemberNotificationType::memberId,
                        r -> r.notificationType().name()));
        Map<Long, List<String>> tokensByMember = fcmTokenRepository.findTokensByMemberIdIn(memberIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MemberToken::memberId,
                        Collectors.mapping(MemberToken::token, Collectors.toList())));

        int processed = 0;
        for (Long memberId : memberIds) {
            String notificationType = typeByMember.getOrDefault(memberId, NotificationType.PUSH.name());
            List<String> tokens = tokensByMember.getOrDefault(memberId, List.of());
            sendToMember(memberId, alertId, title, body, notificationType, tokens);

            processed++;
            if (processed % FLUSH_INTERVAL == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        long fanoutNanos = System.nanoTime() - fanoutStartNanos;
        log.info("회원 팬아웃 완료 - alertId: {}, 대상: {}명, time={}ms, avg={}ms/명",
                alertId, memberIds.size(), fanoutNanos / 1_000_000,
                fanoutNanos / 1_000_000.0 / memberIds.size());
    }

    private void sendToGuestTokens(List<String> regionCodes, Long alertId, String title, String body) {
        try {
            List<String> tokens = guestFcmRegionRepository
                    .findAllByLegalDistrictCodeIn(regionCodes)
                    .stream()
                    .map(r -> r.getFcmToken())
                    .distinct()
                    .toList();

            if (tokens.isEmpty()) return;

            log.info("게스트 알림 발송 대상: {}개 토큰", tokens.size());

            // 알림 클릭 시 상세 페이지로 이동할 수 있도록 alertId를 함께 전달
            String alertIdStr = String.valueOf(alertId);
            if (tokens.size() == 1) {
                fcmSendService.sendToToken(tokens.get(0), title, body,
                        NotificationType.PUSH.name(), alertIdStr);
            } else {
                fcmSendService.sendToTokens(tokens, title, body,
                        NotificationType.PUSH.name(), alertIdStr);
            }
        } catch (Exception e) {
            log.error("게스트 알림 발송 실패: {}", e.getMessage());
        }
    }

    private void sendToMember(Long memberId, Long alertId, String title, String body,
                               String notificationType, List<String> tokens) {
        try {
            // NONE이면 발송 안 함
            if (NotificationType.NONE.name().equals(notificationType)) return;

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