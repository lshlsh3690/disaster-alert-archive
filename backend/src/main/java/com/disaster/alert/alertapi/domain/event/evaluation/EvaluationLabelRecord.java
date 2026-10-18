package com.disaster.alert.alertapi.domain.event.evaluation;

/** 라벨 CSV 한 행. */
public record EvaluationLabelRecord(
        long alertAId,
        long alertBId,
        EvaluationLabelType label,
        EvaluationStratum stratum,
        String subType) {
}
