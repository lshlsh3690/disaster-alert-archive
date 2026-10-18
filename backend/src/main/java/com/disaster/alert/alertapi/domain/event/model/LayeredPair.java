package com.disaster.alert.alertapi.domain.event.model;

public record LayeredPair(double distance, boolean sameEvent, PairLayer layer) {
}
