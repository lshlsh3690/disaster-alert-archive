package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.model.DisasterEvent;

import java.time.LocalDateTime;

/**
 * 이벤트 목록 응답 한 건.
 *
 * <p>{@code active} 는 저장값이 아니라 조회 시점 기준 파생값
 * (마지막 알림 후 cooldown 이내 → 진행 중).
 */
public record EventListResponse(
        Long id,
        String eventTitle,
        String primaryDisasterType,
        String primaryRegionName,
        boolean active,
        LocalDateTime firstAlertAt,
        LocalDateTime lastAlertAt,
        int alertCount,
        String translatedTitle,
        String language
) {
    public static EventListResponse of(DisasterEvent e, LocalDateTime now) {
        return of(e, now, null, null);
    }

    /** translatedTitle/language 는 lang=ko(또는 미지원) 시 null (기존 클라 호환). */
    public static EventListResponse of(DisasterEvent e, LocalDateTime now, String translatedTitle, String language) {
        return new EventListResponse(
                e.getId(),
                e.getEventTitle(),
                e.getPrimaryDisasterType(),
                e.getPrimaryRegionName(),
                e.isActive(now),
                e.getFirstAlertAt(),
                e.getLastAlertAt(),
                e.getAlertCount(),
                translatedTitle,
                language
        );
    }
}
