package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterAlert;
import com.disaster.alert.alertapi.domain.disasteralert.repository.DisasterAlertRepository;
import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.notification.model.FcmToken;
import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.repository.FcmTokenRepository;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 팬아웃 "개선 전(N+1 + flush/clear 없음)" vs "개선 후(벌크 조회 + flush/clear)"를
 * 같은 테스트 안에서 동시에 보존해 직접 비교 측정한다. 두 구현은 {@link FanoutBenchmarkHarness}에
 * 나란히 남겨두고, 이 테스트는 합성 데이터를 시드해 양쪽에 똑같이 흘려보낸 뒤 소요시간만 비교한다.
 *
 * <p><b>주의 — 이 테스트의 절대 수치는 운영 측정치(docs/PERF_NOTIFICATION_FANOUT.md의
 * 133초→22초)와 다르다.</b> 합성 규모(기본 {@value #MEMBER_COUNT}명)와 로컬 DB 환경이 운영과
 * 다르기 때문에 배수도 다르게 나올 수 있다. 여기서 검증하는 것은 "N+1 + 세션 누적이 있는 경로가
 * 없는 경로보다 느리다"는 방향성이지, 운영 수치의 재현이 아니다 — 운영 규모 실측은
 * {@code backend/loadtest/} 참고.
 *
 * <p>워밍업·반복측정 없이 단발 실행 시간을 비교하는 타이밍 기반 테스트라 CI 자원 경합만으로도
 * 플레이키해질 수 있다 — {@code @Tag("benchmark")}로 표시해 기본 {@code test} 태스크에서
 * 제외하고(build.gradle), {@code ./gradlew benchmarkTest}로만 수동 실행한다(realApi 태그와
 * 동일한 패턴).
 */
@Slf4j
@IntegrationTest
@Import(FanoutBenchmarkHarness.class)
@TestPropertySource(properties = "fcm.dry-run=true")
@Tag("benchmark")
class AlertNotificationFanoutBenchmarkTest {

    private static final int MEMBER_COUNT = 3000;

    @Autowired
    private FanoutBenchmarkHarness harness;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private NotificationPreferenceRepository preferenceRepository;
    @Autowired
    private FcmTokenRepository fcmTokenRepository;
    @Autowired
    private DisasterAlertRepository disasterAlertRepository;

    private final List<Member> createdMembers = new ArrayList<>();
    private final List<DisasterAlert> createdAlerts = new ArrayList<>();

    @Test
    @DisplayName("개선 전(N+1, flush/clear 없음)이 개선 후(벌크 조회, flush/clear 주기)보다 느리다")
    void 개선_전후_팬아웃_소요시간을_비교한다() {
        // given
        List<Long> memberIds = seedMembers(MEMBER_COUNT);
        DisasterAlert legacyAlert = seedAlert();
        DisasterAlert optimizedAlert = seedAlert();

        // when
        long legacyMs = measureMs(() ->
                harness.legacyFanout(memberIds, legacyAlert.getId(), "[재난문자] 테스트", "본문"));
        long optimizedMs = measureMs(() ->
                harness.optimizedFanout(memberIds, optimizedAlert.getId(), "[재난문자] 테스트", "본문"));

        log.info("팬아웃 벤치마크 - 회원수: {}, 개선 전: {}ms, 개선 후: {}ms, 비율: {}배",
                MEMBER_COUNT, legacyMs, optimizedMs, legacyMs / (double) optimizedMs);

        // then
        assertThat(optimizedMs).isLessThan(legacyMs);
    }

    @AfterEach
    void cleanUp() {
        // member/disaster_alert 삭제 시 notification_preference, fcm_token, user_notification_log는
        // 전부 ON DELETE CASCADE라 DB가 알아서 함께 지운다 (V8/V9/V10 마이그레이션 참고).
        if (!createdMembers.isEmpty()) memberRepository.deleteAllInBatch(createdMembers);
        if (!createdAlerts.isEmpty()) disasterAlertRepository.deleteAllInBatch(createdAlerts);
    }

    private List<Long> seedMembers(int count) {
        String runId = UUID.randomUUID().toString();

        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String unique = "fanout-bench-" + i + "-" + runId;
            members.add(Member.create(unique + "@test.com", "password", unique, MemberRole.USER));
        }
        members = memberRepository.saveAll(members);
        createdMembers.addAll(members);

        List<NotificationPreference> preferences = new ArrayList<>(count);
        List<FcmToken> tokens = new ArrayList<>(count);
        for (Member member : members) {
            preferences.add(NotificationPreference.builder()
                    .member(member)
                    .notificationType(NotificationType.PUSH)
                    .minRiskScore(0)
                    .build());
            tokens.add(FcmToken.builder()
                    .member(member)
                    .token("fanout-bench-token-" + member.getId())
                    .deviceType("WEB")
                    .build());
        }
        preferenceRepository.saveAll(preferences);
        fcmTokenRepository.saveAll(tokens);

        return members.stream().map(Member::getId).toList();
    }

    private DisasterAlert seedAlert() {
        DisasterAlert alert = disasterAlertRepository.save(
                DisasterAlert.builder()
                        .sn(System.nanoTime()) // unique 제약만 맞추면 되는 벤치마크 전용 값
                        .message("팬아웃 벤치마크용 테스트 알림")
                        .disasterType("기타")
                        .build()
        );
        createdAlerts.add(alert);
        return alert;
    }

    private long measureMs(Runnable action) {
        long startNanos = System.nanoTime();
        action.run();
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
