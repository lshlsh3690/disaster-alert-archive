package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.UserNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationLogRepository extends JpaRepository<UserNotificationLog, Long> {
    boolean existsByMemberIdAndAlertId(Long memberId, Long alertId);
}