package com.disaster.alert.alertapi.domain.archive.dto;

import java.time.LocalDateTime;

/**
 * 사건 영구 링크 해석 응답 (GET /api/v1/archive/events/{sn}).
 *
 * @param sn               요청한 sn
 * @param bound            그 알림이 지금 어떤 사건에 묶여 있는지
 * @param eventId          bound 일 때만. 프론트 서버 컴포넌트가 기존 사건 상세 API 를 부르기 위한 내부 값(링크·화면 노출 금지)
 * @param representativeSn bound 일 때만. 화면에 표시하는 영구 링크는 항상 이 값으로 만든다
 * @param alert            bound=false 일 때만. 묶이지 않은 알림의 원문 요약
 */
public record ArchiveEventResolveResponse(
        Long sn,
        boolean bound,
        Long eventId,
        Long representativeSn,
        Alert alert
) {
    public record Alert(
            Long alertId,
            Long sn,
            String message,
            LocalDateTime createdAt,
            String disasterType,
            String emergencyLevel
    ) {
    }
}
