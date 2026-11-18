package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.DayStatus;
import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 "날짜별 일일 채점" 응답(계약: specs/008 contracts 1절). 요청 범위의 모든 날이 최신 날짜 먼저로 들어간다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있고, 채우는 로직은 {@code ClusteringDashboardService} 의 Green 에서 쓴다.
 */
public record ClusteringDashboardDailyScoresResponse(LocalDate from, LocalDate to, List<Day> days) {

    /**
     * 날짜 하나. 기록이 없는 날(NO_RECORD)은 날짜와 상태만 채우고 나머지는 null 이라 JSON 에서 빠진다.
     * 점수·비용은 계산할 수 없을 때도 null 이다(0 으로 얼버무리지 않는다).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Day(LocalDate date, DayStatus status,
                      Integer incidentCount, Integer pairCount,
                      Integer correctlyMerged, Integer overMerged, Integer missed,
                      Integer unsureCount, Integer formatFailureCount,
                      Ratio purity, Ratio completeness, Double score,
                      Judge judge,
                      Long promptTokens, Long completionTokens, Double estimatedCostUsd) {
    }

    /** 그날 채점에 쓴 판사. 일치율은 그날 기록에 복사된 당시 값이고, model 은 판사 버전 목록에서 찾는다. */
    public record Judge(String version, String model, Ratio sameAgreement, Ratio diffAgreement) {
    }
}
