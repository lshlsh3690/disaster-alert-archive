package com.disaster.alert.alertapi.domain.event.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class LayeredPairMetrics {

    private LayeredPairMetrics() {
    }

    public static Map<PairLayer, PairSeparationMetrics.Result> byLayer(List<LayeredPair> pairs) {
        Map<PairLayer, PairSeparationMetrics.Result> result = new EnumMap<>(PairLayer.class);
        for (PairLayer layer : PairLayer.values()) {
            List<LabeledPair> inLayer = pairs.stream()
                    .filter(p -> p.layer() == layer)
                    .map(p -> new LabeledPair(p.distance(), p.sameEvent()))
                    .toList();
            boolean computable = inLayer.stream().anyMatch(LabeledPair::sameEvent)
                    && inLayer.stream().anyMatch(p -> !p.sameEvent());
            // 계산 불가 층은 0 같은 값을 채우지 않고 생략한다 — 지표가 있는 것처럼 보이면 오독된다.
            if (computable) {
                result.put(layer, PairSeparationMetrics.compute(inLayer));
            }
        }
        return result;
    }
}
