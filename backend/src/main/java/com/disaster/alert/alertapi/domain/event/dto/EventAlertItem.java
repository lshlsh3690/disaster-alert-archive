package com.disaster.alert.alertapi.domain.event.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 타임라인의 재난문자 한 건.
 *
 * <p>{@code sequenceNo} 는 이벤트 내 순서(1부터). 시간순 정렬과 함께 사용.
 */
public record EventAlertItem(
        Long alertId,
        int sequenceNo,
        String message,
        String disasterType,
        String emergencyLevel,
        LocalDateTime createdAt,
        List<String> regionNames,
        String translatedMessage,
        String translatedDisasterType,
        List<String> translatedRegionNames,
        String language
) {
}
