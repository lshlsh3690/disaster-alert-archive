package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.Map;

/** 쌍 단위 평가 결과. UNSURE 는 모든 지표에서 빠지고 excludedUnsure 로만 센다. */
public record PairwiseReport(
        Confusion overall,
        Map<EvaluationStratum, Confusion> byStratum,
        List<ErrorCase> errors,
        int excludedUnsure) {
}
