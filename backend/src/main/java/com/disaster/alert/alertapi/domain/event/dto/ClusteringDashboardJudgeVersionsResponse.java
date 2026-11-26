package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 "판사 버전 이력" 응답(008 계약 4절, specs/014 계약 1절). 판사 버전 목록 순서(최신 먼저) 그대로 담는다.
 */
public record ClusteringDashboardJudgeVersionsResponse(List<Version> versions) {

    /**
     * 버전 하나. 일치율은 그 버전 최신 측정의 쌍별 판정과 현재 사람 라벨로 센 값이고(라벨을 고치면 바뀐다), 합계만 옮긴 옛
     * 측정이면 저장된 합계다({@code recomputable=false}). 측정 기록이 없거나 셀 수 없는 버전은 measuredOn·두 일치율·recomputable·
     * unmeasuredPairs 가 null 이다. 첫날·마지막 날·일수는 일일 채점 기록에서 구하고, 채점 기록이 없으면 날짜 null, 일수 0 이다.
     *
     * @param unmeasuredPairs 셋에 SAME/DIFF 라벨이 있는데 그 측정에 판정이 없는 쌍 수(합계만 있는 측정이면 null)
     */
    public record Version(String version, String model, LocalDate measuredOn,
                          Ratio sameAgreement, Ratio diffAgreement,
                          Boolean recomputable, Integer unmeasuredPairs,
                          LocalDate firstScoredDate, LocalDate lastScoredDate, int scoredDays) {
    }
}
