package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link ThresholdSweep} 테스트 — 거리 임계값별 병합 예측의 precision/recall/F1.
 * 외부 의존성 없는 순수 로직이므로 순수 JUnit 단위 테스트로 작성한다.
 */
class ThresholdSweepTest {

    private static final double EPS = 1e-9;

    private static final List<LabeledPair> PAIRS = List.of(
            new LabeledPair(0.1, true),
            new LabeledPair(0.3, true),
            new LabeledPair(0.2, false),
            new LabeledPair(0.5, false));

    @Test
    @DisplayName("distance == maxDistance 는 병합이다(경계 포함): max=0.2 -> tp1 fp1 fn1")
    void boundaryIsMerged() {
        List<ThresholdSweep.Row> rows = ThresholdSweep.sweep(PAIRS, List.of(0.2));

        ThresholdSweep.Row row = rows.get(0);
        assertThat(row.maxDistance()).isEqualTo(0.2, within(EPS));
        assertThat(row.tp()).isEqualTo(1); // 0.1 same
        assertThat(row.fp()).isEqualTo(1); // 0.2 diff (경계, 병합)
        assertThat(row.fn()).isEqualTo(1); // 0.3 same (미병합)
        assertThat(row.precision()).isEqualTo(0.5, within(EPS));
        assertThat(row.recall()).isEqualTo(0.5, within(EPS));
        assertThat(row.f1()).isEqualTo(0.5, within(EPS));
    }

    @Test
    @DisplayName("max=0.3 -> tp2 fp1 fn0, precision 2/3, recall 1, f1 0.8")
    void handComputedRow() {
        ThresholdSweep.Row row = ThresholdSweep.sweep(PAIRS, List.of(0.3)).get(0);

        assertThat(row.tp()).isEqualTo(2);
        assertThat(row.fp()).isEqualTo(1);
        assertThat(row.fn()).isEqualTo(0);
        assertThat(row.precision()).isEqualTo(2.0 / 3.0, within(EPS));
        assertThat(row.recall()).isEqualTo(1.0, within(EPS));
        assertThat(row.f1()).isEqualTo(0.8, within(EPS));
    }

    @Test
    @DisplayName("아무것도 병합하지 않으면(tp+fp=0) precision=0, recall=0, f1=0 이고 NaN이 아니다")
    void noMergeGivesZerosNotNaN() {
        ThresholdSweep.Row row = ThresholdSweep.sweep(PAIRS, List.of(0.05)).get(0);

        assertThat(row.tp()).isZero();
        assertThat(row.fp()).isZero();
        assertThat(row.fn()).isEqualTo(2);
        assertThat(row.precision()).isEqualTo(0.0);
        assertThat(row.recall()).isEqualTo(0.0);
        assertThat(row.f1()).isEqualTo(0.0);
        assertThat(Double.isNaN(row.precision())).isFalse();
        assertThat(Double.isNaN(row.f1())).isFalse();
    }

    @Test
    @DisplayName("정답 같은 사건 쌍이 없으면(tp+fn=0) recall=0, f1=0 이고 NaN이 아니다")
    void noPositivesGivesZeroRecall() {
        List<LabeledPair> onlyDiff = List.of(new LabeledPair(0.1, false), new LabeledPair(0.4, false));

        ThresholdSweep.Row row = ThresholdSweep.sweep(onlyDiff, List.of(0.2)).get(0);

        assertThat(row.tp()).isZero();
        assertThat(row.fp()).isEqualTo(1);
        assertThat(row.fn()).isZero();
        assertThat(row.precision()).isEqualTo(0.0);
        assertThat(row.recall()).isEqualTo(0.0);
        assertThat(row.f1()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("입력 maxDistances 순서를 그대로 유지해 반환한다(정렬하지 않음)")
    void preservesInputOrder() {
        List<ThresholdSweep.Row> rows = ThresholdSweep.sweep(PAIRS, List.of(0.5, 0.1, 0.3));

        assertThat(rows).extracting(ThresholdSweep.Row::maxDistance)
                .containsExactly(0.5, 0.1, 0.3);
    }

    @Test
    @DisplayName("bestByF1: F1이 가장 큰 행을 고른다")
    void bestByF1PicksMax() {
        List<ThresholdSweep.Row> rows = ThresholdSweep.sweep(PAIRS, List.of(0.05, 0.2, 0.3, 0.5));

        ThresholdSweep.Row best = ThresholdSweep.bestByF1(rows);

        assertThat(best.maxDistance()).isEqualTo(0.3, within(EPS)); // f1 0.8 이 최대
        assertThat(best.f1()).isEqualTo(0.8, within(EPS));
    }

    @Test
    @DisplayName("bestByF1: F1 동률이면 더 작은 maxDistance(보수적)를 고른다 — 입력 순서와 무관")
    void bestByF1TieBreaksToSmallerDistance() {
        ThresholdSweep.Row loose = new ThresholdSweep.Row(0.6, 2, 1, 0, 2.0 / 3.0, 1.0, 0.8);
        ThresholdSweep.Row tight = new ThresholdSweep.Row(0.3, 2, 1, 0, 2.0 / 3.0, 1.0, 0.8);

        assertThat(ThresholdSweep.bestByF1(List.of(loose, tight))).isEqualTo(tight);
        assertThat(ThresholdSweep.bestByF1(List.of(tight, loose))).isEqualTo(tight);
    }

    @Test
    @DisplayName("bestByF1: 빈 리스트면 IllegalArgumentException")
    void bestByF1EmptyThrows() {
        assertThatThrownBy(() -> ThresholdSweep.bestByF1(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
