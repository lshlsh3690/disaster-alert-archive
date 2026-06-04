package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 이벤트 상세 응답 — 메타 + 타임라인(소속 재난문자 시간순).
 */
public record EventDetailResponse(
        Long id,
        String eventTitle,
        String primaryDisasterType,
        String primaryRegionName,
        boolean active,
        LocalDateTime firstAlertAt,
        LocalDateTime lastAlertAt,
        int alertCount,
        List<EventAlertItem> timeline
) {
    public static EventDetailResponse of(DisasterEvent e, LocalDateTime now, List<EventAlertItem> timeline) {
        return new EventDetailResponse(
                e.getId(),
                e.getEventTitle(),
                e.getPrimaryDisasterType(),
                e.getPrimaryRegionName(),
                e.isActive(now),
                e.getFirstAlertAt(),
                e.getLastAlertAt(),
                e.getAlertCount(),
                timeline
        );
    }
}
