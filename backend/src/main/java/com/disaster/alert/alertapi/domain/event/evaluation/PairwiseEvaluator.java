package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.EnumMap;
import java.util.List;

// Red 단계 뼈대 — 구현은 Green 에서
public final class PairwiseEvaluator {

    private PairwiseEvaluator() {
    }

    public static PairwiseReport evaluate(List<EvaluatedPair> pairs) {
        return new PairwiseReport(
                new Confusion(0, 0, 0, 0, 0.0, 0.0, 0.0),
                new EnumMap<>(EvaluationStratum.class),
                List.of(),
                0);
    }
}
