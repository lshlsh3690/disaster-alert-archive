package com.disaster.alert.alertapi.domain.event.dto;

import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.WrongSide;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import com.disaster.alert.alertapi.domain.event.model.ClusteringDailyScorePair.Disagreement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 "불일치 쌍" 응답(계약 2절). 시스템 배정과 판사 판정이 어긋난 쌍(과병합·과소병합)만 페이지로 나눠 담는다.
 *
 * <p>Red 단계 뼈대 - 값을 담는 구조만 있다.
 */
public record ClusteringDashboardDisagreementsResponse(int page, int size, long totalElements, int totalPages,
                                                       List<Pair> pairs) {

    /**
     * 불일치 쌍 하나. eventA/eventB 는 채점 시점의 시스템 이벤트 id(미배정이면 null).
     * humanLabel·criteriaDate 는 사람이 라벨을 매겼을 때만, wrongSide 는 라벨이 SAME/DIFF 일 때만 채워진다(아니면 null).
     */
    public record Pair(LocalDate date, Disagreement kind, JudgeVerdict judgeVerdict,
                       AlertSummary alertA, AlertSummary alertB,
                       Long eventA, Long eventB,
                       Label humanLabel, LocalDate criteriaDate, WrongSide wrongSide) {
    }

    /** 알림 요약. 알림을 찾을 수 없으면 id 만 채우고 나머지는 null(화면은 "원문 없음"). */
    public record AlertSummary(long id, LocalDateTime sentAt, String type, String message) {
    }
}
