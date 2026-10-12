package com.disaster.alert.alertapi.domain.event.model;

import java.util.Comparator;
import java.util.List;

/** 거리 임계값별 병합 예측의 precision/recall/F1. distance <= maxDistance 이면 병합으로 본다. */
public final class ThresholdSweep {

    private ThresholdSweep() {
    }

    public record Row(double maxDistance, int tp, int fp, int fn, double precision, double recall, double f1) {
    }

    public static List<Row> sweep(List<LabeledPair> pairs, List<Double> maxDistances) {
        return maxDistances.stream().map(max -> row(pairs, max)).toList();
    }

    private static Row row(List<LabeledPair> pairs, double max) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        for (LabeledPair p : pairs) {
            boolean merged = p.distance() <= max;
            if (merged && p.sameEvent()) {
                tp++;
            } else if (merged) {
                fp++;
            } else if (p.sameEvent()) {
                fn++;
            }
        }
        double precision = ratio(tp, tp + fp);
        double recall = ratio(tp, tp + fn);
        double f1 = (precision + recall) == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
        return new Row(max, tp, fp, fn, precision, recall, f1);
    }

    // 분모 0 은 NaN 이 아니라 0.0 — NaN 은 이후 비교·출력을 조용히 오염시킨다.
    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : (double) numerator / denominator;
    }

    /** F1 최대 행. 동률이면 더 작은 maxDistance(보수적 병합)를 고른다. */
    public static Row bestByF1(List<Row> rows) {
        return rows.stream()
                .max(Comparator.comparingDouble(Row::f1)
                        .thenComparing(Comparator.comparingDouble(Row::maxDistance).reversed()))
                .orElseThrow(() -> new IllegalArgumentException("rows 가 비어 있습니다."));
    }
}
