package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.Optional;

/**
 * 쌍 단위 평가 결과. 맞게 분리한 DIFF 쌍은 세지 않는다.
 * UNSURE 와 이벤트 미배정 쌍은 개수에서 빠지고 제외 건수로만 센다.
 *
 * @param merged 맞게 묶음(SAME, 같은 이벤트)
 * @param missed 놓침(SAME, 다른 이벤트)
 * @param wrongMerged 잘못 묶음(DIFF, 같은 이벤트)
 */
public record PairwiseReport(
        int merged,
        int missed,
        int wrongMerged,
        int excludedUnsure,
        int excludedUnassigned,
        List<ErrorPair> missedPairs,
        List<ErrorPair> wrongMergedPairs) {

    // Red 단계 뼈대 — 구현은 Green 에서
    /** missed ÷ (merged + missed). 분모가 0 이면 empty. */
    public Optional<Double> underMergeRate() {
        return Optional.empty();
    }

    // Red 단계 뼈대 — 구현은 Green 에서
    /** wrongMerged ÷ (merged + wrongMerged). 분모가 0 이면 empty. */
    public Optional<Double> overMergeRate() {
        return Optional.empty();
    }
}
