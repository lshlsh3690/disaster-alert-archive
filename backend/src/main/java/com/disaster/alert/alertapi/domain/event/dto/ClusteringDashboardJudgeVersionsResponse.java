package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 "판사 버전 이력" 응답(계약 4절). 판사 버전 목록 순서(최신 먼저) 그대로 담는다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringDashboardJudgeVersionsResponse(List<Version> versions) {

    /**
     * 버전 하나. 일치율은 판사 버전 목록의 현재 값이고, 첫날·마지막 날·일수는 일일 채점 기록에서 구한다.
     * 채점 기록이 없는 버전은 firstScoredDate·lastScoredDate 가 null, scoredDays 가 0 이다.
     */
    public record Version(String version, String model, LocalDate measuredOn,
                          Ratio sameAgreement, Ratio diffAgreement,
                          LocalDate firstScoredDate, LocalDate lastScoredDate, int scoredDays) {
    }
}
