package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "다른 사건은 지금만큼만 붙일 때, 같은 사건을 몇 쌍 붙이나"를 세는 순수 규칙(specs/012 research R2, JPA·Spring import 없음).
 * 모델마다 거리 척도가 달라 같은 거리 값 대신 붙이는 다른 사건 쌍 수를 맞춰 비교한다.
 */
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
     * 거리 비교는 운영 병합 규칙처럼 이하(≤)다. 다른 사건의 K번째·K+1번째 거리가 같으면 기준 거리가 둘 다 안에 넣어
     * candidateDiffMerged 가 K 보다 커진다 — 호출한 쪽이 K 와 비교해 리포트에 그 사실을 적는다.
     * 후보에 기준선의 쌍이 없으면 같은 쌍끼리 비교한 것이 아니므로 예외로 멈춘다.
     *
     * @param baseline      기준선 모델의 대상 쌍 거리
     * @param candidate     후보 모델의 같은 대상 쌍 거리(순서는 달라도 된다)
     * @param baselinePoint 기준선 지점(바로 병합 0.15, LLM 후보 0.40)
     */
    public static Result compare(List<PairDistance> baseline, List<PairDistance> candidate, double baselinePoint) {
        // 1) 후보 거리를 쌍 번호로 찾을 수 있게 한다 — 목록 순서가 달라도 같은 쌍끼리 맞춘다
        Map<PairKey, PairDistance> candidateByKey = new HashMap<>();
        candidate.forEach(c -> candidateByKey.put(key(c), c));

        // 2) K: 기준선이 기준선 지점 이하로 붙이는 다른 사건 쌍 수
        int k = (int) baseline.stream()
                .filter(b -> b.label() == Label.DIFF && b.distance() <= baselinePoint).count();

        // 3) 후보의 다른 사건 쌍을 가까운 순으로 놓고 K번째·K+1번째의 가운데를 기준 거리로 삼는다
        //    (거리가 같으면 알림 번호 순 — 같은 입력에 같은 결과가 나오게)
        List<PairDistance> diffs = candidate.stream().filter(c -> c.label() == Label.DIFF)
                .sorted(Comparator.comparingDouble(PairDistance::distance)
                        .thenComparingLong(PairDistance::alertA).thenComparingLong(PairDistance::alertB))
                .toList();
        int kept = Math.min(k, diffs.size());
        double threshold = middle(diffs, kept);
        List<PairKey> thresholdPairs = new ArrayList<>();
        if (kept > 0) {
            thresholdPairs.add(key(diffs.get(kept - 1)));
        }
        if (kept < diffs.size()) {
            thresholdPairs.add(key(diffs.get(kept)));
        }

        // 4) 같은 사건 쌍을 기준선 지점과 후보 기준 거리로 각각 세고, 안팎이 바뀐 쌍을 모은다
        return countSame(baseline, candidateByKey, baselinePoint, k, threshold, thresholdPairs,
                (int) diffs.stream().filter(d -> d.distance() <= threshold).count());
    }

    // K번째와 K+1번째의 가운데. K=0 이면 0 과, K 가 쌍 수 이상이면 마지막 거리 자신과 맞춘다(한쪽 이웃이 없는 경우)
    private static double middle(List<PairDistance> sortedDiffs, int kept) {
        if (sortedDiffs.isEmpty()) {
            return 0.0;
        }
        if (kept == 0) {
            return sortedDiffs.get(0).distance() / 2;
        }
        if (kept == sortedDiffs.size()) {
            return sortedDiffs.get(kept - 1).distance();
        }
        return (sortedDiffs.get(kept - 1).distance() + sortedDiffs.get(kept).distance()) / 2;
    }

    private static Result countSame(List<PairDistance> baseline, Map<PairKey, PairDistance> candidateByKey,
                                    double baselinePoint, int k, double threshold, List<PairKey> thresholdPairs,
                                    int candidateDiffMerged) {
        int baselineSame = 0;
        int candidateSame = 0;
        List<PairKey> inside = new ArrayList<>();
        List<PairKey> outside = new ArrayList<>();
        for (PairDistance b : baseline) {
            if (b.label() != Label.SAME) {
                continue;
            }
            PairDistance c = candidateByKey.get(key(b));
            if (c == null) {
                throw new IllegalArgumentException("후보 거리에 기준선의 쌍이 없습니다: " + b.alertA() + ", " + b.alertB());
            }
            boolean baselineIn = b.distance() <= baselinePoint;
            boolean candidateIn = c.distance() <= threshold;
            baselineSame += baselineIn ? 1 : 0;
            candidateSame += candidateIn ? 1 : 0;
            if (!baselineIn && candidateIn) {
                inside.add(key(b));
            } else if (baselineIn && !candidateIn) {
                outside.add(key(b));
            }
        }
        return new Result(k, threshold, thresholdPairs, baselineSame, candidateSame, candidateDiffMerged, inside, outside);
    }

    private static PairKey key(PairDistance d) {
        return new PairKey(d.alertA(), d.alertB());
    }
}
