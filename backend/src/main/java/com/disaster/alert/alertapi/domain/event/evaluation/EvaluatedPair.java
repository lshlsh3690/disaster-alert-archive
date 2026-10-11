package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 평가 대상 쌍 하나. 사람 라벨과 시스템 예측을 함께 담는다.
 *
 * @param goldEventInCandidates 한쪽 알림이 속한 이벤트가 다른 쪽 알림의 후보 목록(top-K)에 있었는가. 모르면 null.
 */
public record EvaluatedPair(
        long alertAId,
        long alertBId,
        EvaluationLabelType label,
        EvaluationStratum stratum,
        boolean predictedSame,
        Boolean goldEventInCandidates) {
}
