package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 평가 대상 쌍 하나. 사람 라벨과 "두 알림이 같은 이벤트에 있는가"를 담는다.
 *
 * @param assigned 두 알림 모두 이벤트에 배정됐는가. 아니면 평가에서 제외한다.
 * @param predictedSame 두 알림이 같은 이벤트에 있는가.
 * @param facts 오류 쌍 목록에 붙일 사실 정보. 모르면 null.
 */
public record EvaluatedPair(
        long alertAId,
        long alertBId,
        EvaluationLabelType label,
        boolean assigned,
        boolean predictedSame,
        PairFacts facts) {
}
