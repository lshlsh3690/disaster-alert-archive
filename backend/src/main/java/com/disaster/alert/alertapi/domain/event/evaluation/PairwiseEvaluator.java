package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;

/**
 * 사람 라벨과 "같은 이벤트에 있는가"를 쌍 단위로 비교해 묶음·놓침·잘못 묶음을 센다.
 * UNSURE 와 미배정 쌍은 개수에서 빼고 제외 건수로만 센다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class PairwiseEvaluator {

    private PairwiseEvaluator() {
    }

    public static PairwiseReport evaluate(List<EvaluatedPair> pairs) {
        return new PairwiseReport(0, 0, 0, 0, 0, List.of(), List.of());
    }
}
