package com.disaster.alert.alertapi.domain.disasteralert.dto;

import java.time.LocalDateTime;

/**
 * sn 으로 찾은 알림의 요약 (disasteralert 맥락 → archive 맥락 전달용).
 *
 * @param eventId           현재 속한 사건 ID. 사건에 묶이지 않은 알림이면 null
 * @param representativeSn  그 사건의 대표 알림 sn(가장 먼저 발령된 알림). 사건에 묶이지 않았으면 null.
 *                          eventId 와 같은 조회 문장에서 읽어 서로 다른 시점의 값이 섞이지 않는다
 */
public record AlertSnLookup(
        Long alertId,
        Long sn,
        String message,
        LocalDateTime createdAt,
        String disasterType,
        String emergencyLevel,
        Long eventId,
        Long representativeSn
) {
}
