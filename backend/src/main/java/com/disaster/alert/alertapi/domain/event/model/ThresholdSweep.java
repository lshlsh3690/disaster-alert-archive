package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;

// Red 단계 뼈대 — 구현은 Green 에서
public final class ThresholdSweep {

    private ThresholdSweep() {
    }

    public record Row(double maxDistance, int tp, int fp, int fn, double precision, double recall, double f1) {
    }

    public static List<Row> sweep(List<LabeledPair> pairs, List<Double> maxDistances) {
        return List.of();
    }

    public static Row bestByF1(List<Row> rows) {
        return null;
    }
}
