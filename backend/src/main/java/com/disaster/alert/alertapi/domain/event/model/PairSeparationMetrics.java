package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;

// Red 단계 뼈대 — 구현은 Green 에서
public final class PairSeparationMetrics {

    private PairSeparationMetrics() {
    }

    public record Result(double auc, double margin, double meanSameDistance, double meanDiffDistance,
                         int sameCount, int diffCount) {
    }

    public static Result compute(List<LabeledPair> pairs) {
        return new Result(0, 0, 0, 0, 0, 0);
    }
}
