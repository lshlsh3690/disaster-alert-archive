package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dto.ClusteringDashboardDisagreementsResponse.AlertSummary;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel.Relation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면 "평가 셋 라벨" 목록 응답. 라벨 쌍을 페이지로 나눠 두 알림 원문과 함께 담고,
 * labelCounts 는 필터와 상관없이 그 평가 셋 전체의 라벨별 개수다.
 */
public record EvalLabelsResponse(int page, int size, long totalElements, int totalPages,
                                 LabelCounts labelCounts, List<Pair> pairs) {

    /** 라벨별 개수. */
    public record LabelCounts(long same, long diff, long unsure) {
    }

    /** 라벨 쌍 하나. 알림을 찾을 수 없으면 AlertSummary 는 id 만 채워진다(화면은 "원문 없음"). */
    public record Pair(long id, Integer q, Relation relation, Label label, LocalDate criteriaDate,
                       LocalDateTime updatedAt, AlertSummary alertA, AlertSummary alertB) {
    }
}
