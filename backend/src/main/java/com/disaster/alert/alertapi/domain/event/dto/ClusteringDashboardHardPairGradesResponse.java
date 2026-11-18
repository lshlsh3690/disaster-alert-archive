package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dashboard.ClusteringDashboardMetrics.Ratio;

import java.util.List;

/**
 * 관리자 대시보드 "어려운 쌍 판사 성적" 응답(계약 3절). 지금 불일치인 쌍 중 사람 라벨이 있는 것을 판사 버전별로 센다.
 * 라벨이 하나도 없으면 labeledPairs 0, versions 빈 목록이다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringDashboardHardPairGradesResponse(int labeledPairs, List<Version> versions) {

    /**
     * 판사 버전 하나. judgeRight·systemRight 의 분모는 SAME/DIFF 라벨 쌍이고, 모르겠음(UNSURE)은 분모에서 빼고
     * unsureCount 로만 남는다. model 은 버전 목록에 없는 버전이면 null.
     */
    public record Version(String version, String model, Ratio judgeRight, Ratio systemRight, int unsureCount) {
    }
}
