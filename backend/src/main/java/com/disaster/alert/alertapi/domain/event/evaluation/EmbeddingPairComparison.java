package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * "다른 사건은 지금만큼만 붙일 때, 같은 사건을 몇 쌍 붙이나"를 세는 순수 규칙(specs/012 research R2, JPA·Spring import 없음).
 * 모델마다 거리 척도가 달라 같은 거리 값 대신 붙이는 다른 사건 쌍 수를 맞춰 비교한다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmbeddingPairComparison {

    /** 대상 쌍 하나의 어느 모델에서의 코사인 거리. label 은 SAME 또는 DIFF 다. */
    public record PairDistance(long alertA, long alertB, Label label, double distance) {
    }

    /** 원문을 다시 찾기 위한 쌍 식별자. */
    public record PairKey(long alertA, long alertB) {
    }

    /**
     * @param allowedDiffCount    K — 기준선이 기준선 지점 이하에서 붙이는 다른 사건 쌍 수
     * @param threshold           후보 모델의 기준 거리
     * @param thresholdPairs      기준 거리를 정한 후보의 K번째·K+1번째 다른 사건 쌍
     * @param baselineSameMerged  기준선이 기준선 지점 이하로 붙이는 같은 사건 쌍 수
     * @param candidateSameMerged 후보가 기준 거리 이하로 붙이는 같은 사건 쌍 수
     * @param candidateDiffMerged 후보가 기준 거리 이하로 붙이는 다른 사건 쌍 수
     * @param newlyInside         기준선에서는 기준 밖, 후보에서는 기준 안인 같은 사건 쌍
     * @param newlyOutside        기준선에서는 기준 안, 후보에서는 기준 밖인 같은 사건 쌍
     */
    public record Result(int allowedDiffCount, double threshold, List<PairKey> thresholdPairs,
                         int baselineSameMerged, int candidateSameMerged, int candidateDiffMerged,
                         List<PairKey> newlyInside, List<PairKey> newlyOutside) {
    }

    /**
     * @param baseline      기준선 모델의 대상 쌍 거리
     * @param candidate     후보 모델의 같은 대상 쌍 거리(순서는 달라도 된다)
     * @param baselinePoint 기준선 지점(바로 병합 0.15, LLM 후보 0.40)
     */
    public static Result compare(List<PairDistance> baseline, List<PairDistance> candidate, double baselinePoint) {
        return new Result(0, 0.0, List.of(), 0, 0, 0, List.of(), List.of());
    }
}
