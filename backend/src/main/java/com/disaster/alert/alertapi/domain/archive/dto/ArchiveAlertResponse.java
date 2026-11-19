package com.disaster.alert.alertapi.domain.archive.dto;

import java.time.LocalDateTime;

/**
 * 원문 기록(기록관 스토리 1) — {@code GET /api/v1/archive/alerts/{sn}}.
 *
 * <p>내부 번호(alertId)는 기존 상세 화면으로 가는 링크용이고, 인용·공유 링크는 항상 sn 으로 만든다.
 * 사건 ID 는 내보내지 않는다 — 사건에 묶였는지({@code inEvent})만 알려 주고 사건 링크는 이 sn 으로 만든다
 * ({@code /archive/events/{sn}} 이 지금 속한 사건으로 해석한다).
 *
 * @param agency 본문에 발신 기관 표기가 없으면 null
 */
public record ArchiveAlertResponse(
        Long sn,
        Long alertId,
        String message,
        LocalDateTime createdAt,
        LocalDateTime modifiedDate,
        String disasterType,
        String emergencyLevel,
        String originalRegion,
        boolean inEvent,
        Agency agency
) {
    /**
     * @param label 본문 표기 그대로 — 인용문에 쓴다
     * @param name  표기가 가리키는 기관 이름. 기관이 아직 지정되지 않았으면 null(백필 전 등)
     */
    public record Agency(String label, String name) {
    }
}
