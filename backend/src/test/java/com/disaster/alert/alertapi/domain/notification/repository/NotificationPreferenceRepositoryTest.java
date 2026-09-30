package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.member.model.MemberRole;
import com.disaster.alert.alertapi.domain.member.repository.MemberRepository;
import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.global.testsupport.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * {@link NotificationPreferenceRepository#findNotificationTypesByMemberIdIn} 벌크 조회 테스트.
 *
 * <p>배경: {@code AlertNotificationService.sendToMembers()} 가 회원 1만 명을 순회하며
 * {@code findByMemberId} 를 건별로 호출하는 N+1 이 발송 루프를 17분 53초까지 늘어지게 만든다.
 * 이 메서드는 한 번의 IN 쿼리 + JPQL 생성자 프로젝션(DTO)으로 대체해 영속성 컨텍스트에
 * 엔티티가 쌓이지 않게 하려는 목적이다.
 */
@IntegrationTest
class NotificationPreferenceRepositoryTest {

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @Transactional
    @DisplayName("알림설정이 저장된 회원만 반환한다 — 설정 없는 회원은 결과에서 빠지고, " +
            "NONE으로 저장된 회원도(꺼짐도 유효한 설정이므로) 포함되어야 한다")
    void 알림설정이_있는_회원만_반환하고_각각_정확히_매칭된다() {
        // given
        Member memberA = memberRepository.save(newMember("push"));
        Member memberB = memberRepository.save(newMember("none-pref")); // 알림설정 행 없음
        Member memberC = memberRepository.save(newMember("none-type"));

        notificationPreferenceRepository.save(
                NotificationPreference.builder()
                        .member(memberA)
                        .notificationType(NotificationType.PUSH)
                        .minRiskScore(0)
                        .build());
        notificationPreferenceRepository.save(
                NotificationPreference.builder()
                        .member(memberC)
                        .notificationType(NotificationType.NONE)
                        .minRiskScore(0)
                        .build());
        notificationPreferenceRepository.flush();

        // when
        List<NotificationPreferenceRepository.MemberNotificationType> result =
                notificationPreferenceRepository.findNotificationTypesByMemberIdIn(
                        List.of(memberA.getId(), memberB.getId(), memberC.getId()));

        // then
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(
                        NotificationPreferenceRepository.MemberNotificationType::memberId,
                        NotificationPreferenceRepository.MemberNotificationType::notificationType)
                .containsExactlyInAnyOrder(
                        tuple(memberA.getId(), NotificationType.PUSH),
                        tuple(memberC.getId(), NotificationType.NONE));
    }

    private Member newMember(String label) {
        String unique = label + "-" + UUID.randomUUID();
        return Member.create(unique + "@test.com", "password", unique, MemberRole.USER);
    }
}
