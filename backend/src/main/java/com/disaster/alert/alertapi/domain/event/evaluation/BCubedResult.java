package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.OptionalDouble;

/**
 * BCubedScorer 가 낸 결과. 비율은 퍼센트 값(0~100)이고, 점수 대상이 0 이면 empty 다.
 *
 * @param scored             점수 대상 알림 수
 * @param excludedUnsure     분류 불가라 뺀 알림 수
 * @param unassigned         이벤트에 배정되지 않은 알림 수 (점수에는 혼자인 이벤트로 포함되고, 리포트에 알리려고 센다)
 * @param precisionRate      맞게 묶은 비율(%) — 알림마다 "내 이벤트 안에서 나와 같은 사건인 비율" 의 평균
 * @param completenessRate   빠짐없이 묶은 비율(%) — 알림마다 "나와 같은 사건 중 내 이벤트에 든 비율" 의 평균
 */
public record BCubedResult(int scored, int excludedUnsure, int unassigned,
                           OptionalDouble precisionRate, OptionalDouble completenessRate) {

    /**
     * 점수(0~100) = 두 비율의 조화평균. 어느 한쪽이라도 없으면 empty.
     * 조화평균을 쓰는 이유: 전부 한 이벤트로 묶어 빠짐없이 묶은 비율만 높이거나 전부 쪼개 맞게 묶은 비율만 높여도,
     * 낮은 쪽에 끌려 점수가 내려간다. 두 비율의 합이 0(분모 0)이면 empty — PairCountResult.score 와 같은 기준.
     */
    public OptionalDouble score() {
        if (precisionRate.isEmpty() || completenessRate.isEmpty()) {
            return OptionalDouble.empty();
        }
        double p = precisionRate.getAsDouble();
        double c = completenessRate.getAsDouble();
        return p + c == 0 ? OptionalDouble.empty() : OptionalDouble.of(2 * p * c / (p + c));
    }
}
