package com.disaster.alert.alertapi.domain.event.evaluation;

import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.DIFF;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.SAME;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.UNSURE;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationStratum.BOUNDARY;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationStratum.GENERAL;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationStratum.TRAP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PairwiseEvaluatorTest {

    private static final double EPS = 1e-9;
    private static long seq = 0;

    private static EvaluatedPair pair(EvaluationLabelType label, EvaluationStratum stratum,
                                      boolean predictedSame, Boolean gold) {
        long n = ++seq;
        return new EvaluatedPair(n * 10, n * 10 + 1, label, stratum, predictedSame, gold);
    }

    private static void add(List<EvaluatedPair> list, int count, EvaluationLabelType label,
                            EvaluationStratum stratum, boolean predictedSame) {
        for (int i = 0; i < count; i++) {
            list.add(pair(label, stratum, predictedSame, null));
        }
    }

    @Test
    @DisplayName("라벨과 예측 조합이 tp/fp/fn/tn 으로 분류되고 정밀도·재현율·F1 이 계산된다")
    void classifiesAndComputesMetrics() {
        List<EvaluatedPair> pairs = new ArrayList<>();
        add(pairs, 2, SAME, GENERAL, true);   // tp
        add(pairs, 1, DIFF, GENERAL, true);   // fp
        add(pairs, 1, SAME, GENERAL, false);  // fn
        add(pairs, 3, DIFF, GENERAL, false);  // tn

        Confusion c = PairwiseEvaluator.evaluate(pairs).overall();

        assertThat(c.tp()).isEqualTo(2);
        assertThat(c.fp()).isEqualTo(1);
        assertThat(c.fn()).isEqualTo(1);
        assertThat(c.tn()).isEqualTo(3);
        assertThat(c.precision()).isCloseTo(2.0 / 3, within(EPS));
        assertThat(c.recall()).isCloseTo(2.0 / 3, within(EPS));
        assertThat(c.f1()).isCloseTo(2.0 / 3, within(EPS));
    }

    @Test
    @DisplayName("UNSURE 는 지표와 errors 에서 제외되고 excludedUnsure 로만 센다")
    void excludesUnsure() {
        List<EvaluatedPair> pairs = new ArrayList<>();
        add(pairs, 1, SAME, GENERAL, true);
        add(pairs, 2, UNSURE, GENERAL, true);
        add(pairs, 1, UNSURE, TRAP, false);

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        assertThat(r.excludedUnsure()).isEqualTo(3);
        assertThat(r.overall().tp()).isEqualTo(1);
        assertThat(r.overall().fp() + r.overall().fn() + r.overall().tn()).isZero();
        assertThat(r.errors()).isEmpty();
        assertThat(r.byStratum()).containsOnlyKeys(GENERAL);
    }

    @Test
    @DisplayName("분모가 0 이면 NaN 이 아니라 0.0")
    void zeroDenominatorsYieldZero() {
        List<EvaluatedPair> onlyTn = new ArrayList<>();
        add(onlyTn, 2, DIFF, GENERAL, false);
        Confusion tnOnly = PairwiseEvaluator.evaluate(onlyTn).overall();
        assertThat(tnOnly.tn()).isEqualTo(2);
        assertThat(tnOnly.precision()).isEqualTo(0.0);
        assertThat(tnOnly.recall()).isEqualTo(0.0);
        assertThat(tnOnly.f1()).isEqualTo(0.0);

        List<EvaluatedPair> onlyFn = new ArrayList<>();
        add(onlyFn, 2, SAME, GENERAL, false);
        Confusion fnOnly = PairwiseEvaluator.evaluate(onlyFn).overall();
        assertThat(fnOnly.fn()).isEqualTo(2);
        assertThat(fnOnly.precision()).isEqualTo(0.0);
        assertThat(fnOnly.recall()).isEqualTo(0.0);
        assertThat(fnOnly.f1()).isEqualTo(0.0);

        List<EvaluatedPair> onlyFp = new ArrayList<>();
        add(onlyFp, 2, DIFF, GENERAL, true);
        Confusion fpOnly = PairwiseEvaluator.evaluate(onlyFp).overall();
        assertThat(fpOnly.fp()).isEqualTo(2);
        assertThat(fpOnly.precision()).isEqualTo(0.0);
        assertThat(fpOnly.recall()).isEqualTo(0.0);
        assertThat(fpOnly.f1()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("층별로 독립 계산하고, 데이터 없는 층은 키가 없으며, 순서는 GENERAL, BOUNDARY, TRAP")
    void computesPerStratum() {
        List<EvaluatedPair> pairs = new ArrayList<>();
        add(pairs, 3, SAME, TRAP, false);     // 함정 층은 전부 fn
        add(pairs, 2, SAME, GENERAL, true);   // 일반 층은 완벽
        add(pairs, 2, DIFF, GENERAL, false);

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        assertThat(r.byStratum().keySet()).containsExactly(GENERAL, TRAP);
        Confusion general = r.byStratum().get(GENERAL);
        assertThat(general.tp()).isEqualTo(2);
        assertThat(general.tn()).isEqualTo(2);
        assertThat(general.precision()).isCloseTo(1.0, within(EPS));
        assertThat(general.recall()).isCloseTo(1.0, within(EPS));
        assertThat(general.f1()).isCloseTo(1.0, within(EPS));
        Confusion trap = r.byStratum().get(TRAP);
        assertThat(trap.fn()).isEqualTo(3);
        assertThat(trap.tp()).isZero();
        assertThat(trap.recall()).isEqualTo(0.0);

        assertThat(r.overall().tp()).isEqualTo(2);
        assertThat(r.overall().fn()).isEqualTo(3);
        assertThat(r.overall().recall()).isCloseTo(2.0 / 5, within(EPS));
    }

    @Test
    @DisplayName("층별 혼동 행렬 합이 overall 과 일치한다")
    void stratumSumsMatchOverall() {
        List<EvaluatedPair> pairs = new ArrayList<>();
        add(pairs, 2, SAME, GENERAL, true);
        add(pairs, 1, DIFF, GENERAL, true);
        add(pairs, 1, SAME, BOUNDARY, false);
        add(pairs, 2, DIFF, BOUNDARY, false);
        add(pairs, 1, SAME, TRAP, true);
        add(pairs, 1, DIFF, TRAP, false);

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        assertThat(r.byStratum().keySet()).containsExactly(GENERAL, BOUNDARY, TRAP);
        int tp = 0, fp = 0, fn = 0, tn = 0;
        for (Confusion c : r.byStratum().values()) {
            tp += c.tp();
            fp += c.fp();
            fn += c.fn();
            tn += c.tn();
        }
        assertThat(r.overall().tp()).isEqualTo(tp).isEqualTo(3);
        assertThat(r.overall().fp()).isEqualTo(fp).isEqualTo(1);
        assertThat(r.overall().fn()).isEqualTo(fn).isEqualTo(1);
        assertThat(r.overall().tn()).isEqualTo(tn).isEqualTo(3);
    }

    @Test
    @DisplayName("오류 원인: fp 는 OVER_MERGE, fn 은 후보 포함 여부에 따라 DECISION/RETRIEVAL/UNKNOWN, 입력 순서 유지")
    void classifiesErrorCauses() {
        EvaluatedPair tp = new EvaluatedPair(1, 2, SAME, GENERAL, true, true);
        EvaluatedPair fp = new EvaluatedPair(3, 4, DIFF, BOUNDARY, true, null);
        EvaluatedPair fnDecision = new EvaluatedPair(5, 6, SAME, TRAP, false, Boolean.TRUE);
        EvaluatedPair tn = new EvaluatedPair(7, 8, DIFF, GENERAL, false, false);
        EvaluatedPair fnRetrieval = new EvaluatedPair(9, 10, SAME, GENERAL, false, Boolean.FALSE);
        EvaluatedPair unsure = new EvaluatedPair(11, 12, UNSURE, TRAP, false, false);
        EvaluatedPair fnUnknown = new EvaluatedPair(13, 14, SAME, BOUNDARY, false, null);

        PairwiseReport r = PairwiseEvaluator.evaluate(
                List.of(tp, fp, fnDecision, tn, fnRetrieval, unsure, fnUnknown));

        assertThat(r.errors()).containsExactly(
                new ErrorCase(3, 4, BOUNDARY, ErrorCause.OVER_MERGE),
                new ErrorCase(5, 6, TRAP, ErrorCause.DECISION_MISS),
                new ErrorCase(9, 10, GENERAL, ErrorCause.RETRIEVAL_MISS),
                new ErrorCase(13, 14, BOUNDARY, ErrorCause.UNKNOWN_MISS));
    }

    @Test
    @DisplayName("빈 리스트는 예외 없이 모두 0 인 report")
    void emptyInput() {
        PairwiseReport r = PairwiseEvaluator.evaluate(List.of());

        assertThat(r).isNotNull();
        assertThat(r.overall()).isEqualTo(new Confusion(0, 0, 0, 0, 0.0, 0.0, 0.0));
        assertThat(r.byStratum()).isEmpty();
        assertThat(r.errors()).isEmpty();
        assertThat(r.excludedUnsure()).isZero();
    }

    @Test
    @DisplayName("null 입력은 IllegalArgumentException")
    void nullInputThrows() {
        assertThatThrownBy(() -> PairwiseEvaluator.evaluate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("실제 규모(147쌍): tp 69, fn 42, tn 36, fp 0 이면 precision 1.0, recall 69/111")
    void realScale() {
        List<EvaluatedPair> pairs = new ArrayList<>();
        add(pairs, 40, SAME, GENERAL, true);
        add(pairs, 20, SAME, BOUNDARY, true);
        add(pairs, 9, SAME, TRAP, true);
        add(pairs, 10, SAME, GENERAL, false);
        add(pairs, 12, SAME, BOUNDARY, false);
        add(pairs, 20, SAME, TRAP, false);
        add(pairs, 16, DIFF, GENERAL, false);
        add(pairs, 10, DIFF, BOUNDARY, false);
        add(pairs, 10, DIFF, TRAP, false);
        assertThat(pairs).hasSize(147);

        Confusion c = PairwiseEvaluator.evaluate(pairs).overall();

        double recall = 69.0 / 111;
        assertThat(c.tp()).isEqualTo(69);
        assertThat(c.fn()).isEqualTo(42);
        assertThat(c.tn()).isEqualTo(36);
        assertThat(c.fp()).isZero();
        assertThat(c.precision()).isCloseTo(1.0, within(EPS));
        assertThat(c.recall()).isCloseTo(recall, within(EPS));
        assertThat(c.f1()).isCloseTo(2 * 1.0 * recall / (1.0 + recall), within(EPS));
    }
}
