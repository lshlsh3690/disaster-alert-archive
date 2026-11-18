package com.disaster.alert.alertapi.domain.event.dto;

import java.time.LocalDateTime;

/**
 * 연도별 주요 사건 조회 결과 한 행 (event 맥락 → archive 맥락 전달용).
 *
 * <p>사건 ID 는 일부러 담지 않는다 — 사건 번호는 재클러스터링 때 바뀌므로 기록관은 대표 알림 sn 으로만 가리킨다.
 *
 * @param representativeSn 사건에서 가장 먼저 발령된 알림의 sn (같은 시각이면 작은 sn)
 */
public record MajorEventRow(
        Long representativeSn,
        String eventTitle,
        String primaryDisasterType,
        String primaryRegionName,
        LocalDateTime firstAlertAt,
        LocalDateTime lastAlertAt,
        int alertCount
) {
}
