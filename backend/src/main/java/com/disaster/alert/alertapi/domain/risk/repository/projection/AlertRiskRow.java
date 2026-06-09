package com.disaster.alert.alertapi.domain.risk.repository.projection;

/**
 * findByAlertId 조회 결과 — alert → event → event_region_impact 조인 행.
 * 한 alertId 당 region 개수만큼 행이 반환된다.
 */
public record AlertRiskRow(
        Long eventId,
        String eventTitle,
        String disasterType,
        String regionCode,
        double impactScore
) {}
