package com.disaster.alert.alertapi.domain.event.service;

import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyChecksResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDailyScoresResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse;
import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardJudgeVersionsResponse;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드 조회 service - 일일 채점·구조 검사 기록을 읽어 {@code ClusteringDashboardMetrics} 계산과 합친다.
 *
 * <p>Red 단계 뼈대 - 빈 응답만 돌려준다. 저장소 조회·범위 검증·계산 조율은 Green 에서 쓴다.
 */
@Service
public class ClusteringDashboardService {

    /** 날짜별 일일 채점. */
    public ClusteringDashboardDailyScoresResponse dailyScores(LocalDate from, LocalDate to) {
        return new ClusteringDashboardDailyScoresResponse(from, to, List.of());
    }

    /** 불일치 쌍 한 페이지. kind 가 null 이면 두 종류 모두. */
    public ClusteringDashboardDisagreementsResponse disagreements(LocalDate from, LocalDate to, Disagreement kind,
                                                                  int page, int size) {
        return new ClusteringDashboardDisagreementsResponse(page, size, 0, 0, List.of());
    }

    /** 날짜별 구조 검사. */
    public ClusteringDashboardDailyChecksResponse dailyChecks(LocalDate from, LocalDate to) {
        return new ClusteringDashboardDailyChecksResponse(from, to, List.of());
    }

    /** 판사 버전 이력. */
    public ClusteringDashboardJudgeVersionsResponse judgeVersions() {
        return new ClusteringDashboardJudgeVersionsResponse(List.of());
    }
}
