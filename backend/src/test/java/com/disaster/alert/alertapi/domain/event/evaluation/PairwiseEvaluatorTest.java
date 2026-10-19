package com.disaster.alert.alertapi.domain.event.evaluation;

import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.DIFF;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.SAME;
import static com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelType.UNSURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PairwiseEvaluatorTest {

    private static final PairFacts NO_FACTS = null;

    private static EvaluatedPair pair(long id, EvaluationLabelType label, boolean assigned, boolean predictedSame,
                                      PairFacts facts) {
        return new EvaluatedPair(id, id + 1, label, assigned, predictedSame, facts);
    }

    /**
     * 평가는 SAME/DIFF 라벨과 "같은 이벤트인가"만 본다. SAME+같은 이벤트=묶음, SAME+다른 이벤트=놓침,
     * DIFF+같은 이벤트=잘못 묶음이고, 맞게 분리한 DIFF 는 세지 않는다. 비율의 분모에도 들어가지 않는다.
     * Red: 뼈대는 개수를 모두 0 으로 돌려주고 비율은 항상 empty 라 개수·비율 단언이 실패한다
     * (마지막 "분모 0 이면 empty" 단언은 뼈대와 같아 우연히 통과하는 가드다).
     */
    @Test
    @DisplayName("묶음·놓침·잘못 묶음을 세고 과소병합율·과병합율을 낸다 (분모 0 이면 empty)")
    void countsAndRates() {
        List<EvaluatedPair> pairs = List.of(
                pair(10, SAME, true, true, NO_FACTS),    // 묶음
                pair(20, SAME, true, true, NO_FACTS),    // 묶음
                pair(30, SAME, true, false, NO_FACTS),   // 놓침
                pair(40, SAME, true, false, NO_FACTS),   // 놓침
                pair(50, DIFF, true, true, NO_FACTS),    // 잘못 묶음
                pair(60, DIFF, true, false, NO_FACTS),   // 맞게 분리 — 세지 않는다
                pair(70, DIFF, true, false, NO_FACTS),
                pair(80, DIFF, true, false, NO_FACTS));

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        assertThat(r.merged()).isEqualTo(2);
        assertThat(r.missed()).isEqualTo(2);
        assertThat(r.wrongMerged()).isEqualTo(1);
        // 과소병합율 = 놓침 ÷ (묶음+놓침) = 2/4
        assertThat(r.underMergeRate()).hasValueSatisfying(v -> assertThat(v).isCloseTo(0.5, within(1e-9)));
        // 과병합율 = 잘못 묶음 ÷ (묶음+잘못 묶음) = 1/3 — 맞게 분리한 DIFF 3건은 분모에 없다
        assertThat(r.overMergeRate()).hasValueSatisfying(v -> assertThat(v).isCloseTo(1.0 / 3, within(1e-9)));

        // 맞게 분리한 DIFF 만 있으면 두 분모가 모두 0 이라 비율이 없다 (NaN·0.0 아님)
        PairwiseReport onlySeparated = PairwiseEvaluator.evaluate(List.of(pair(90, DIFF, true, false, NO_FACTS)));
        assertThat(onlySeparated.underMergeRate()).isEmpty();
        assertThat(onlySeparated.overMergeRate()).isEmpty();
    }

    /**
     * 모르겠음(UNSURE)과 이벤트 미배정 쌍은 개수에서 빼고 각각 제외 건수로만 센다. UNSURE 가 우선이라
     * UNSURE 이면서 미배정인 쌍은 excludedUnsure 에만 센다.
     * Red: 뼈대는 모든 값이 0 이라 merged=1 단언에서 실패한다 (제외 건수 단언도 실패).
     */
    @Test
    @DisplayName("UNSURE·미배정 쌍은 개수에서 빠지고 제외 건수로 센다 (UNSURE 우선)")
    void excludesUnsureAndUnassigned() {
        List<EvaluatedPair> pairs = List.of(
                pair(10, SAME, true, true, NO_FACTS),         // 묶음 1건만 남는다
                pair(20, SAME, false, false, NO_FACTS),       // 미배정 SAME: 놓침이 아니라 제외
                pair(30, DIFF, false, false, NO_FACTS),       // 미배정 DIFF: 제외
                pair(40, UNSURE, true, false, NO_FACTS),      // UNSURE
                pair(50, UNSURE, false, false, NO_FACTS));    // UNSURE + 미배정: UNSURE 로만 센다

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        assertThat(r.merged()).isEqualTo(1);
        assertThat(r.missed()).isZero();
        assertThat(r.excludedUnassigned()).isEqualTo(2);
        assertThat(r.excludedUnsure()).isEqualTo(2);
    }

    /**
     * 놓친 쌍과 잘못 묶은 쌍은 목록으로 나오고, 원인 추정 없이 입력의 사실(PairFacts)이 그대로 붙는다.
     * Red: 뼈대는 두 목록을 비워 돌려줘 크기 단언에서 실패한다.
     */
    @Test
    @DisplayName("놓친 쌍·잘못 묶은 쌍 목록에 사실(PairFacts)이 담긴다")
    void errorPairsCarryFacts() {
        PairFacts missedFacts = new PairFacts("호우/산불", false, 3, 0.412);
        PairFacts wrongFacts = new PairFacts("폭염", true, 1, null);   // 임베딩 없음 -> 거리 null
        List<EvaluatedPair> pairs = List.of(
                pair(10, SAME, true, true, NO_FACTS),
                pair(20, SAME, true, false, missedFacts),
                pair(30, DIFF, true, true, wrongFacts));

        PairwiseReport r = PairwiseEvaluator.evaluate(pairs);

        // 묶음(10)은 오류 목록에 없고, 놓침(20)과 잘못 묶음(30)만 각 목록에 한 건씩
        assertThat(r.missedPairs()).containsExactly(new ErrorPair(20, 21, missedFacts));
        assertThat(r.wrongMergedPairs()).containsExactly(new ErrorPair(30, 31, wrongFacts));
    }
}
