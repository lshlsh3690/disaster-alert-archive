package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.dto.MemberNotificationType;
import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByMemberId(Long memberId);

    // FROM ... WHERE member.id IN 이라 설정 행이 없는 회원은 자연히 결과에서 빠진다(INNER 조인 성격) —
    // NONE 도 "설정이 저장된" 행이므로 포함된다. 존재하지 않는 회원과 "PUSH로 취급해야 하는" 회원을
    // 구분하는 건 호출부(AlertNotificationService)의 fallback 몫이다.
    // MemberNotificationType이 최상위 클래스여야 하는 이유는 그 record의 javadoc 참고.
    @Query("SELECT new com.disaster.alert.alertapi.domain.notification.dto.MemberNotificationType(p.member.id, p.notificationType) " +
            "FROM NotificationPreference p WHERE p.member.id IN :memberIds")
    List<MemberNotificationType> findNotificationTypesByMemberIdIn(@Param("memberIds") List<Long> memberIds);
}