package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 사람 라벨과 시스템 예측을 쌍 단위로 비교해 혼동 행렬·층별 지표·오류 원인을 계산한다.
 * UNSURE 는 정답을 알 수 없어 지표를 왜곡하므로 모든 계산에서 빼고 개수만 센다.
 */
public final class PairwiseEvaluator {

    private PairwiseEvaluator() {
    }

    public static PairwiseReport evaluate(List<EvaluatedPair> pairs) {
        if (pairs == null) {
            throw new IllegalArgumentException("pairs must not be null");
        }
        List<EvaluatedPair> judged = pairs.stream()
                .filter(p -> p.label() != EvaluationLabelType.UNSURE)
                .toList();
        int excludedUnsure = pairs.size() - judged.size();

        return new PairwiseReport(
                confusionOf(judged),
                confusionByStratum(judged),
                collectErrors(judged),
                excludedUnsure);
    }

    // EnumMap 이라 데이터 유무와 무관하게 키 순서가 GENERAL, BOUNDARY, TRAP 으로 고정된다.
    private static Map<EvaluationStratum, Confusion> confusionByStratum(List<EvaluatedPair> judged) {
        Map<EvaluationStratum, Confusion> result = new EnumMap<>(EvaluationStratum.class);
        for (EvaluationStratum stratum : EvaluationStratum.values()) {
            List<EvaluatedPair> inStratum = judged.stream()
                    .filter(p -> p.stratum() == stratum)
                    .toList();
            if (!inStratum.isEmpty()) {
                result.put(stratum, confusionOf(inStratum));
            }
        }
        return result;
    }

    private static Confusion confusionOf(List<EvaluatedPair> judged) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (EvaluatedPair p : judged) {
            boolean actualSame = p.label() == EvaluationLabelType.SAME;
            if (actualSame && p.predictedSame()) {
                tp++;
            } else if (!actualSame && p.predictedSame()) {
                fp++;
            } else if (actualSame) {
                fn++;
            } else {
                tn++;
            }
        }
        double precision = ratio(tp, tp + fp);
        double recall = ratio(tp, tp + fn);
        double f1 = ratio(2 * precision * recall, precision + recall);
        return new Confusion(tp, fp, fn, tn, precision, recall, f1);
    }

    private static List<ErrorCase> collectErrors(List<EvaluatedPair> judged) {
        List<ErrorCase> errors = new ArrayList<>();
        for (EvaluatedPair p : judged) {
            boolean actualSame = p.label() == EvaluationLabelType.SAME;
            if (actualSame == p.predictedSame()) {
                continue;
            }
            errors.add(new ErrorCase(p.alertAId(), p.alertBId(), p.stratum(), causeOf(p)));
        }
        return errors;
    }

    // 여기 오는 쌍은 이미 오류다. 예측이 same 이면 DIFF 를 합친 것(fp), 아니면 SAME 을 놓친 것(fn).
    private static ErrorCause causeOf(EvaluatedPair p) {
        if (p.predictedSame()) {
            return ErrorCause.OVER_MERGE;
        }
        Boolean inCandidates = p.goldEventInCandidates();
        if (inCandidates == null) {
            return ErrorCause.UNKNOWN_MISS;
        }
        return inCandidates ? ErrorCause.DECISION_MISS : ErrorCause.RETRIEVAL_MISS;
    }

    // 분모 0 이면 NaN 대신 0.0 — 해당 클래스 표본이 없다는 뜻이지 오류가 아니다.
    private static double ratio(double numerator, double denominator) {
        return denominator == 0 ? 0.0 : numerator / denominator;
    }
}
