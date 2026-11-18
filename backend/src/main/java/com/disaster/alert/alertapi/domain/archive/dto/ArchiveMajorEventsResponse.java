package com.disaster.alert.alertapi.domain.archive.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 연도별 주요 사건 응답 (GET /api/v1/archive/major-events).
 *
 * <p>순위 번호·점수 필드는 일부러 없다(FR-013) — 목록 순서만 있다. 사건 ID 도 없다(재클러스터링 때 바뀌므로).
 *
 * @param year   조회한 연도
 * @param years  사건이 존재하는 연도 목록(내림차순) — 화면 연도 선택지
 * @param events 포함 알림 수 내림차순 최대 10개
 */
public record ArchiveMajorEventsResponse(
        Integer year,
        List<Integer> years,
        List<Item> events
) {
    /** @param representativeSn 사건 영구 링크(/archive/events/{sn})를 만드는 값 */
    public record Item(
            Long representativeSn,
            String eventTitle,
            String primaryDisasterType,
            String primaryRegionName,
            LocalDateTime firstAlertAt,
            LocalDateTime lastAlertAt,
            int alertCount
    ) {
    }
}
