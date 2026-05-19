package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    // 알림함 전체 조회 (최신순)
    List<NotificationLog> findByMemberIdOrderBySentAtDesc(Long memberId);

    // 알림함 페이징 조회 (컨트롤러용)
    Page<NotificationLog> findByMemberIdOrderBySentAtDesc(Long memberId, Pageable pageable);

    // 읽지 않은 알림 수 (배지)
    long countByMemberIdAndIsReadFalse(Long memberId);

    // 중복 발송 방지 (스케줄러 연동 시 사용)
    boolean existsByMemberIdAndDisasterAlertId(Long memberId, Long disasterAlertId);

    // 읽음 처리용 단건 조회 (본인 소유 검증 포함)
    Optional<NotificationLog> findByIdAndMemberId(Long id, Long memberId);
}
