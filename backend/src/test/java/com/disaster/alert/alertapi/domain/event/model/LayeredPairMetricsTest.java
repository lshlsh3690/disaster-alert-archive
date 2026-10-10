package com.disaster.alert.alertapi.domain.event.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 평가에서 안내성 층 분리 - {@link PairLayer}, {@link LayeredPair}, {@link LayeredPairMetrics}.
 * 외부 의존성 없는 순수 로직이므로 순수 JUnit 단위 테스트로 작성한다.
 */
class LayeredPairMetricsTest {

    private static final double EPS = 1e-9;

    private static LayeredPair p(double d, boolean same, PairLayer layer) {
        return new LayeredPair(d, same, layer);
    }

    @Test
    @DisplayName("PairLayer.of: 둘 다 안내=ADVISORY, 둘 다 사건=INCIDENT, 그 외=MIXED")
    void pairLayerOf() {
        assertThat(PairLayer.of(true, true)).isEqualTo(PairLayer.ADVISORY);
        assertThat(PairLayer.of(false, false)).isEqualTo(PairLayer.INCIDENT);
        assertThat(PairLayer.of(true, false)).isEqualTo(PairLayer.MIXED);
        assertThat(PairLayer.of(false, true)).isEqualTo(PairLayer.MIXED);
    }

    @Test
    @DisplayName("빈 입력이면 빈 맵")
    void emptyInputGivesEmptyMap() {
        assertThat(LayeredPairMetrics.byLayer(List.of())).isEmpty();
    }

    @Test
    @DisplayName("안내층은 완전 겹침(AUC 0.5), 사건층은 완전 분리(AUC 1.0) - 합산과 층별이 다르다")
    void layersAreComputedIndependently() {
        List<LayeredPair> pairs = new ArrayList<>(List.of(
                // 안내층: 같은쌍/다른쌍 거리가 동일 -> 동률뿐
                p(0.30, true, PairLayer.ADVISORY), p(0.30, false, PairLayer.ADVISORY),
                // 사건층: 같은쌍이 전부 더 가깝다
                p(0.05, true, PairLayer.INCIDENT), p(0.10, true, PairLayer.INCIDENT),
                p(0.40, false, PairLayer.INCIDENT), p(0.70, false, PairLayer.INCIDENT)));

        Map<PairLayer, PairSeparationMetrics.Result> result = LayeredPairMetrics.byLayer(pairs);

        assertThat(result.get(PairLayer.ADVISORY).auc()).isEqualTo(0.5, within(EPS));
        assertThat(result.get(PairLayer.ADVISORY).sameCount()).isEqualTo(1);
        assertThat(result.get(PairLayer.ADVISORY).diffCount()).isEqualTo(1);
        assertThat(result.get(PairLayer.INCIDENT).auc()).isEqualTo(1.0, within(EPS));
        assertThat(result.get(PairLayer.INCIDENT).margin()).isEqualTo(0.30, within(EPS));
        assertThat(result.get(PairLayer.INCIDENT).sameCount()).isEqualTo(2);

        // 전체 합산은 층별과 다르다: same{0.05,0.10,0.30} vs diff{0.30,0.40,0.70} -> 8.5/9
        PairSeparationMetrics.Result all = PairSeparationMetrics.compute(
                pairs.stream().map(x -> new LabeledPair(x.distance(), x.sameEvent())).toList());
        assertThat(all.auc()).isNotEqualTo(result.get(PairLayer.ADVISORY).auc());
        assertThat(all.auc()).isNotEqualTo(result.get(PairLayer.INCIDENT).auc());
    }

    @Test
    @DisplayName("한 층의 쌍은 다른 층 지표에 섞이지 않는다 - 사건층 쌍을 추가해도 안내층 결과는 그대로")
    void layerIsolation() {
        List<LayeredPair> advisoryOnly = List.of(
                p(0.2, true, PairLayer.ADVISORY), p(0.5, false, PairLayer.ADVISORY));
        List<LayeredPair> withNoise = new ArrayList<>(advisoryOnly);
        withNoise.add(p(0.9, true, PairLayer.INCIDENT));
        withNoise.add(p(0.1, false, PairLayer.INCIDENT));

        PairSeparationMetrics.Result base = LayeredPairMetrics.byLayer(advisoryOnly).get(PairLayer.ADVISORY);
        PairSeparationMetrics.Result noisy = LayeredPairMetrics.byLayer(withNoise).get(PairLayer.ADVISORY);

        assertThat(noisy.auc()).isEqualTo(base.auc(), within(EPS));
        assertThat(noisy.margin()).isEqualTo(base.margin(), within(EPS));
        assertThat(LayeredPairMetrics.byLayer(withNoise).get(PairLayer.INCIDENT).auc())
                .isEqualTo(0.0, within(EPS));
    }

    @Test
    @DisplayName("같은쌍 또는 다른쌍이 0개라 계산 불가인 층은 예외 없이 결과에서 생략한다")
    void uncomputableLayersAreOmitted() {
        List<LayeredPair> pairs = List.of(
                p(0.1, true, PairLayer.ADVISORY), p(0.2, true, PairLayer.ADVISORY),   // 다른쌍 없음
                p(0.6, false, PairLayer.MIXED),                                          // 같은쌍 없음
                p(0.1, true, PairLayer.INCIDENT), p(0.5, false, PairLayer.INCIDENT));

        Map<PairLayer, PairSeparationMetrics.Result> result = LayeredPairMetrics.byLayer(pairs);

        assertThat(result).containsOnlyKeys(PairLayer.INCIDENT);
    }
}
