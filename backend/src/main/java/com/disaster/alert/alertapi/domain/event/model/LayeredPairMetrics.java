package com.disaster.alert.alertapi.domain.event.model;

import java.util.Map;
import java.util.List;

// Red 단계 뼈대 — 구현은 Green 에서
public final class LayeredPairMetrics {

    private LayeredPairMetrics() {
    }

    public static Map<PairLayer, PairSeparationMetrics.Result> byLayer(List<LayeredPair> pairs) {
        return Map.of();
    }
}
