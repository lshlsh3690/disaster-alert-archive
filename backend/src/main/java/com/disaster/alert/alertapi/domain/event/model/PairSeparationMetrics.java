package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;

/**
 * 같은 사건 쌍과 다른 사건 쌍의 코사인 거리 분리도.
 * 평균 거리는 점수 일괄 이동에도 따라 움직여 분리도 개선과 구분되지 않으므로,
 * 순위 기반인 AUC 와 최악 경계 간격인 margin 을 함께 낸다.
 */
public final class PairSeparationMetrics {

    private PairSeparationMetrics() {
    }

    public record Result(double auc, double margin, double meanSameDistance, double meanDiffDistance,
                         int sameCount, int diffCount) {
    }

    public static Result compute(List<LabeledPair> pairs) {
        double[] same = pairs.stream().filter(LabeledPair::sameEvent).mapToDouble(LabeledPair::distance).toArray();
        double[] diff = pairs.stream().filter(p -> !p.sameEvent()).mapToDouble(LabeledPair::distance).toArray();
        if (same.length == 0 || diff.length == 0) {
            // 한쪽 집단이 없으면 AUC 가 정의되지 않는다. 0.0 같은 값으로 뭉개면 "완전 역전"과 구분되지 않는다.
            throw new IllegalArgumentException("같은 사건 쌍과 다른 사건 쌍이 각각 1개 이상 필요합니다.");
        }

        double wins = 0;
        for (double s : same) {
            for (double d : diff) {
                if (s < d) {
                    wins += 1;
                } else if (s == d) {
                    wins += 0.5;
                }
            }
        }
        double auc = wins / ((double) same.length * diff.length);

        double maxSame = Double.NEGATIVE_INFINITY;
        double sumSame = 0;
        for (double s : same) {
            maxSame = Math.max(maxSame, s);
            sumSame += s;
        }
        double minDiff = Double.POSITIVE_INFINITY;
        double sumDiff = 0;
        for (double d : diff) {
            minDiff = Math.min(minDiff, d);
            sumDiff += d;
        }

        return new Result(auc, minDiff - maxSame, sumSame / same.length, sumDiff / diff.length,
                same.length, diff.length);
    }
}
