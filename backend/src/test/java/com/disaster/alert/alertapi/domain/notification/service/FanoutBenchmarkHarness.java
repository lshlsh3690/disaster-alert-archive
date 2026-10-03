package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.notification.dto.MemberNotificationType;
import com.disaster.alert.alertapi.domain.notification.dto.MemberToken;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.domain.notification.repository.UserNotificationLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 알림 팬아웃 개선 전/후 두 구현을 나란히 보존해 같은 테스트에서 직접 비교 측정할 수 있게 하는
 * 테스트 전용 하니스. {@link AlertNotificationFanoutBenchmarkTest}에서만 쓰인다.
 *
 * <p>{@link #legacyFanout}은 2026-09-30 이전 실제 운영 코드를 그대로 복원한 것이다 — 회원별
 * 개별 조회(N+1)와, 루프 전체를 감싸는 하나의 트랜잭션 안에서 영속성 컨텍스트를 한 번도 비우지
 * 않는 구현 실수가 함께 있었다. {@link #optimizedFanout}은 현재
 * {@link AlertNotificationService#sendToMembers}와 동일한 구조(벌크 조회 + flush/clear
 * 주기)다 — 로직이 바뀌면 이 메서드도 함께 갱신해야 비교가 유효하다.
 *
 * <p>두 메서드 모두 공개 메서드에 직접 {@code @Transactional}을 붙여, 프록시를 통해 호출될 때마다
 * (같은 Spring 빈을 재사용해도) 매번 새 최상위 트랜잭션에서 시작한다 — 원본 버그가 "회원 1만 명
 * 순회 전체가 트랜잭션 하나"였다는 조건을 그대로 재현하기 위함이다. 호출부(테스트)가 자신을
 * {@code @Transactional}로 감싸면 이 메서드들이 그 트랜잭션에 합류해버려 조건이 깨지므로 주의.
 */
// @Component가 아니라 @TestComponent다 — 일반 @Component로 등록하면 TestTypeExcludeFilter가
// 걸러주지 않아 이 패키지를 스캔하는 다른 모든 @SpringBootTest 컨텍스트에도 이 빈이 함께
// 올라간다. @TestComponent는 테스트 전용 빈임을 명시해 메인 컴포넌트 그래프에서 제외시킨다.
@TestComponent
@RequiredArgsConstructor
class FanoutBenchmarkHarness {

    // AlertNotificationService.FLUSH_INTERVAL과 동일한 값 — private 상수라 직접 참조할 수 없어
    // 값만 그대로 복제한다. 운영 코드에서 이 값이 바뀌면 여기도 맞춰야 비교가 유효하다.
    private static final int FLUSH_INTERVAL = 500;

    private final NotificationPreferenceRepository preferenceRepository;
    private final FcmTokenRepository fcmTokenRepository;
    private final UserNotificationLogRepository notificationLogRepository;
    private final FcmSendService fcmSendService;

    @PersistenceContext
    private EntityManager entityManager;

    /** 개선 전 — 회원별 개별 조회(N+1) + flush/clear 없음 (구현 실수 포함, 과거 운영 코드 그대로). */
    @Transactional
    public void legacyFanout(List<Long> memberIds, Long alertId, String title, String body) {
        for (Long memberId : memberIds) {
            String notificationType = preferenceRepository.findByMemberId(memberId)
                    .map(p -> p.getNotificationType().name())
                    .orElse(NotificationType.PUSH.name());
            List<String> tokens = fcmTokenRepository.findAllByMemberId(memberId).stream()
                    .map(FcmToken::getToken)
                    .toList();
            sendToMember(memberId, alertId, title, body, notificationType, tokens);
            // flush/clear 없음 — 영속성 컨텍스트가 루프 내내 계속 쌓인다 (회원당 처리시간이
            // 진행될수록 느려지는 원인, 실측: 31ms → 93ms/명).
        }
    }

    /** 개선 후 — 벌크 조회(N+1 제거) + flush/clear 주기(세션 누적 제거). */
    @Transactional
    public void optimizedFanout(List<Long> memberIds, Long alertId, String title, String body) {
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
    }

    /**
     * 두 버전이 공유하는 발송+로그 저장 로직 — 비교 대상(쿼리 패턴/flush 여부)이 아니므로 동일하게 둔다.
     * 실제 {@link AlertNotificationService#sendToMember}와 달리 NONE 분기·개별 try/catch가
     * 없다 — 시드 데이터가 항상 PUSH라 NONE 분기를 안 타고, dry-run이라 예외 가능성도 낮아
     * 벤치마크 결과에는 영향이 없지만, 회원 1명 실패가 전체 트랜잭션에 번질 수 있다는 점에서
     * 에러 처리까지 "동일 구조"는 아니다 — 쿼리 패턴/flush 비교로 범위를 의도적으로 좁힌 것이다.
     */
    private void sendToMember(Long memberId, Long alertId, String title, String body,
                               String notificationType, List<String> tokens) {
        if (tokens.isEmpty()) return;

        boolean sent = tokens.size() == 1
                ? fcmSendService.sendToToken(tokens.get(0), title, body, notificationType, String.valueOf(alertId))
                : fcmSendService.sendToTokens(tokens, title, body, notificationType, String.valueOf(alertId)) != null;

        notificationLogRepository.save(
                UserNotificationLog.builder()
                        .memberId(memberId)
                        .alertId(alertId)
                        .status(sent ? "SENT" : "FAILED")
                        .notificationType(notificationType)
                        .build()
        );
    }
}
