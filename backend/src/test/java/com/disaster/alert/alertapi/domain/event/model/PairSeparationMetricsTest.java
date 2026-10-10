package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link PairSeparationMetrics} 테스트 — 같은 사건 쌍과 다른 사건 쌍의 코사인 거리 분리도(AUC, margin).
 * 외부 의존성 없는 순수 로직이므로 순수 JUnit 단위 테스트로 작성한다.
 */
class PairSeparationMetricsTest {

    private static final double EPS = 1e-9;

    private static LabeledPair same(double d) {
        return new LabeledPair(d, true);
    }

    private static LabeledPair diff(double d) {
        return new LabeledPair(d, false);
    }

    @Test
    @DisplayName("완전 분리: 같은 사건 쌍 거리가 전부 더 작으면 AUC=1.0, margin은 양수")
    void perfectSeparation() {
        PairSeparationMetrics.Result r = PairSeparationMetrics.compute(
                List.of(same(0.05), same(0.10), diff(0.40), diff(0.70)));

        assertThat(r.auc()).isEqualTo(1.0, within(EPS));
        assertThat(r.margin()).isEqualTo(0.30, within(EPS)); // 0.40 - 0.10
        assertThat(r.sameCount()).isEqualTo(2);
        assertThat(r.diffCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("완전 역전: 같은 사건 쌍 거리가 전부 더 크면 AUC=0.0, margin은 음수")
    void perfectInversion() {
        PairSeparationMetrics.Result r = PairSeparationMetrics.compute(
                List.of(same(0.60), same(0.80), diff(0.10), diff(0.20)));

        assertThat(r.auc()).isEqualTo(0.0, within(EPS));
        assertThat(r.margin()).isEqualTo(-0.70, within(EPS)); // 다른쌍 최소 0.10 - 같은쌍 최대 0.80
    }

    @Test
    @DisplayName("구분 불가: 모든 거리가 동일하면 동률만 있어 AUC=0.5, margin=0")
    void indistinguishableIsHalf() {
        PairSeparationMetrics.Result r = PairSeparationMetrics.compute(
                List.of(same(0.3), same(0.3), diff(0.3), diff(0.3)));

        assertThat(r.auc()).isEqualTo(0.5, within(EPS));
        assertThat(r.margin()).isEqualTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("겹치는 분포 손계산: same{0.1,0.4} vs diff{0.3,0.6} -> 4쌍 중 3승 = AUC 0.75, margin=0.3-0.4=-0.1")
    void overlappingDistributionHandComputed() {
        PairSeparationMetrics.Result r = PairSeparationMetrics.compute(
                List.of(same(0.1), same(0.4), diff(0.3), diff(0.6)));

        // (0.1,0.3) 승, (0.1,0.6) 승, (0.4,0.3) 패, (0.4,0.6) 승
        assertThat(r.auc()).isEqualTo(0.75, within(EPS));
        assertThat(r.margin()).isEqualTo(-0.1, within(EPS));
        assertThat(r.meanSameDistance()).isEqualTo(0.25, within(EPS));
        assertThat(r.meanDiffDistance()).isEqualTo(0.45, within(EPS));
    }

    @Test
    @DisplayName("동률 쌍은 0.5 가산: same{0.2,0.5} vs diff{0.2} -> (0.2 동률 0.5) + (0.5 패 0) = 0.25")
    void tieCountsHalf() {
        PairSeparationMetrics.Result r = PairSeparationMetrics.compute(
                List.of(same(0.2), same(0.5), diff(0.2)));

        assertThat(r.auc()).isEqualTo(0.25, within(EPS));
    }

    @Test
    @DisplayName("입력 순서와 무관하다")
    void orderIndependent() {
        List<LabeledPair> pairs = new ArrayList<>(List.of(same(0.1), same(0.4), diff(0.3), diff(0.6)));
        PairSeparationMetrics.Result base = PairSeparationMetrics.compute(pairs);

        java.util.Collections.reverse(pairs);
        PairSeparationMetrics.Result reversed = PairSeparationMetrics.compute(pairs);

        assertThat(reversed).isEqualTo(base);
    }

    @Test
    @DisplayName("불변: 모든 거리에 같은 상수를 더해도(점수 일괄 상승/하락) AUC와 margin은 변하지 않는다")
    void uniformShiftDoesNotChangeAucOrMargin() {
        List<LabeledPair> base = List.of(same(0.1), same(0.4), diff(0.3), diff(0.6));
        PairSeparationMetrics.Result before = PairSeparationMetrics.compute(base);

        for (double shift : new double[]{0.2, -0.05, 1.5}) {
            List<LabeledPair> shifted = base.stream()
                    .map(p -> new LabeledPair(p.distance() + shift, p.sameEvent()))
                    .toList();
            PairSeparationMetrics.Result after = PairSeparationMetrics.compute(shifted);

            assertThat(after.auc()).isEqualTo(before.auc(), within(EPS));
            assertThat(after.margin()).isEqualTo(before.margin(), within(EPS));
            // 평균은 이동하지만(점수 자체는 바뀜) 그것이 분리도 개선이 아님을 보여준다.
            assertThat(after.meanSameDistance()).isEqualTo(before.meanSameDistance() + shift, within(EPS));
            assertThat(after.meanDiffDistance()).isEqualTo(before.meanDiffDistance() + shift, within(EPS));
        }
    }

    @Test
    @DisplayName("같은 사건 쌍이 0개면 IllegalArgumentException")
    void noSamePairsThrows() {
        assertThatThrownBy(() -> PairSeparationMetrics.compute(List.of(diff(0.3), diff(0.5))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("다른 사건 쌍이 0개면 IllegalArgumentException")
    void noDiffPairsThrows() {
        assertThatThrownBy(() -> PairSeparationMetrics.compute(List.of(same(0.3), same(0.5))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 입력이면 IllegalArgumentException")
    void emptyThrows() {
        assertThatThrownBy(() -> PairSeparationMetrics.compute(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
