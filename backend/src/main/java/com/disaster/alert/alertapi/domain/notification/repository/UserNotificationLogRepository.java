package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserNotificationLogRepository extends JpaRepository<UserNotificationLog, Long> {

    boolean existsByMemberIdAndAlertId(Long memberId, Long alertId);

    @Query("SELECT ul FROM UserNotificationLog ul JOIN FETCH ul.disasterAlert WHERE ul.memberId = :memberId ORDER BY ul.createdAt DESC")
    Page<UserNotificationLog> findByMemberIdWithAlertOrderByCreatedAtDesc(@Param("memberId") Long memberId, Pageable pageable);

    Optional<UserNotificationLog> findByIdAndMemberId(Long id, Long memberId);

    long countByMemberIdAndIsReadFalse(Long memberId);
}