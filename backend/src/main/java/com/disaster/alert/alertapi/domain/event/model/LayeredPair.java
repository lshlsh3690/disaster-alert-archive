package com.disaster.alert.alertapi.domain.event.model;

// Red 단계 뼈대 — 구현은 Green 에서
public record LayeredPair(double distance, boolean sameEvent, PairLayer layer) {
}
