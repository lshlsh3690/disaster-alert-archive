package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByMemberId(Long memberId);

    // 회원 여러 명의 알림설정을 한 번에 조회하기 위한 프로젝션 타입.
    // 엔티티가 아니라 이 record 로 받아 영속성 컨텍스트에 쌓이지 않게 하는 것이 목적 — 아직 미구현
    // (Green 단계에서 JPQL 생성자 프로젝션으로 구현 예정). NotificationPreferenceRepositoryTest 가
    // Red 로 실패하도록 지금은 default 메서드가 예외만 던진다.
    record MemberNotificationType(Long memberId, NotificationType notificationType) {}

    default List<MemberNotificationType> findNotificationTypesByMemberIdIn(List<Long> memberIds) {
        throw new UnsupportedOperationException("아직 구현되지 않았습니다 — Green 단계에서 구현 예정");
    }
}