package com.disaster.alert.alertapi.domain.notification.service;

import com.disaster.alert.alertapi.domain.member.model.Member;
import com.disaster.alert.alertapi.domain.notification.dto.NotificationPreferenceDtos;
import com.disaster.alert.alertapi.domain.notification.model.NotificationPreference;
import com.disaster.alert.alertapi.domain.notification.model.NotificationType;
import com.disaster.alert.alertapi.domain.notification.repository.NotificationPreferenceRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final EntityManager entityManager;

    // 조회 (없으면 기본값 PUSH 반환)
    @Transactional(readOnly = true)
    public NotificationPreferenceDtos.Response getPreference(Long memberId) {
        return preferenceRepository.findByMemberId(memberId)
                .map(NotificationPreferenceDtos.Response::from)
                .orElse(NotificationPreferenceDtos.Response.defaultValue());
    }

    // 생성 또는 수정 (upsert)
    public NotificationPreferenceDtos.Response updatePreference(
            Long memberId, NotificationPreferenceDtos.UpdateRequest request) {

        NotificationPreference preference = preferenceRepository.findByMemberId(memberId)
                .orElseGet(() -> {
                    // 최초 설정 시 신규 생성
                    Member memberRef = entityManager.getReference(Member.class, memberId);
                    return NotificationPreference.builder()
                            .member(memberRef)
                            .notificationType(NotificationType.PUSH)
                            .minRiskScore(0)
                            .build();
                });

        preference.updateNotificationType(request.notificationType());
        preferenceRepository.save(preference);

        return NotificationPreferenceDtos.Response.from(preference);
    }
}