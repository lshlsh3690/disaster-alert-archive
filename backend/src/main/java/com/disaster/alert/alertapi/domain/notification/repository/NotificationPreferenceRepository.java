package com.disaster.alert.alertapi.domain.notification.repository;

import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {
    Optional<NotificationPreference> findByMemberId(Long memberId);
}