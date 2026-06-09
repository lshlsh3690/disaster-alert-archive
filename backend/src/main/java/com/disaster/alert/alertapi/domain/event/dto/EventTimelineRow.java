package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.disasteralert.model.DisasterLevel;

import java.time.LocalDateTime;

/**
 * 타임라인 조회 JPQL 결과 행 (내부용).
 *
 * <p>지역명은 별도 batch 조회 후 서비스에서 {@link EventAlertItem} 로 합친다.
 */
public record EventTimelineRow(
        Long alertId,
        int sequenceNo,
        String message,
        String disasterType,
        DisasterLevel emergencyLevel,
        LocalDateTime createdAt
) {
}
