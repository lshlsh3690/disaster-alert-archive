package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.OptionalDouble;

/**
 * BCubedScorer 가 낸 결과. 비율은 퍼센트 값(0~100)이고, 점수 대상이 0 이면 empty 다.
 *
 * @param scored             점수 대상 알림 수
 * @param excludedUnsure     분류 불가라 뺀 알림 수
 * @param excludedUnassigned 이벤트에 배정되지 않아 뺀 알림 수
 * @param precisionRate      맞게 묶은 비율(%) — 알림마다 "내 이벤트 안에서 나와 같은 사건인 비율" 의 평균
 * @param completenessRate   빠짐없이 묶은 비율(%) — 알림마다 "나와 같은 사건 중 내 이벤트에 든 비율" 의 평균
 */
// Red 단계 뼈대 — 구현은 Green 에서
public record BCubedResult(int scored, int excludedUnsure, int excludedUnassigned,
                           OptionalDouble precisionRate, OptionalDouble completenessRate) {

    /** 점수(0~100) = 두 비율의 조화평균. */
    public OptionalDouble score() {
        return OptionalDouble.empty();
    }
}
