package com.disaster.alert.alertapi.domain.risk.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 위험도 API 응답 DTO 모음.
 */
public final class RiskResponses {

    private RiskResponses() {}

    /** GET /api/regions/risk-map — 전국 시군구 위험도 (히트맵). */
    public record RegionRiskMapItem(
            String regionCode,
            double riskScore,
            Long topEventId,
            LocalDateTime updatedAt
    ) {}

    /** GET /api/regions/{code}/risk — 지역 상세. */
    public record RegionRiskDetail(
            String regionCode,
            double riskScore,
            Long topEventId,
            List<ContributingEvent> contributingEvents,
            LocalDateTime updatedAt
    ) {}

    /** 기여 이벤트(설명용). */
    public record ContributingEvent(
            Long eventId,
            String eventTitle,
            String disasterType,
            double impactScore
    ) {}

    /** GET /api/regions/{code}/risk/history — 시계열. */
    public record RiskHistoryPoint(
            LocalDateTime snapshotAt,
            double riskScore
    ) {}
}
