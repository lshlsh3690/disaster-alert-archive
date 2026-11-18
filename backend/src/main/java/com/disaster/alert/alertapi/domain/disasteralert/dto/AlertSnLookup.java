package com.disaster.alert.alertapi.domain.disasteralert.dto;

import java.time.LocalDateTime;

/**
 * sn 으로 찾은 알림의 요약 (disasteralert 맥락 → archive 맥락 전달용).
 *
 * @param eventId 현재 속한 사건 ID. 사건에 묶이지 않은 알림이면 null
 */
public record AlertSnLookup(
        Long alertId,
        Long sn,
        String message,
        LocalDateTime createdAt,
        String disasterType,
        String emergencyLevel,
        Long eventId
) {
}
