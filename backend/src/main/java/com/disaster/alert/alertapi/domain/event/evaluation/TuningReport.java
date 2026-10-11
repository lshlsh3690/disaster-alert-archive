package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.model.ThresholdSweep;

import java.util.List;
import java.util.Map;

/** 유사도 임계값별 병합 시뮬레이션 곡선. byStratum 은 쌍이 있는 층만 GENERAL, BOUNDARY, TRAP 순서로 담는다. */
public record TuningReport(
        List<ThresholdSweep.Row> overall,
        Map<EvaluationStratum, List<ThresholdSweep.Row>> byStratum
) {
}
