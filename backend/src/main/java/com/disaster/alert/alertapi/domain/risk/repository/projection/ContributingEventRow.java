package com.disaster.alert.alertapi.domain.risk.repository.projection;

/**
 * findContributingEvents 조회 결과의 영속 계층 read model.
 *
 * <p>컨트롤러 응답 DTO({@code RiskResponses.ContributingEvent}) 와 분리한다 —
 * 리포지토리가 프레젠테이션 계층을 의존하지 않도록(의존성 방향 보호).
 * DTO 변환은 {@code RegionRiskQueryService} 에서 한다.
 */
public record ContributingEventRow(
        Long eventId,
        String eventTitle,
        String disasterType,
        double impactScore
) {}
