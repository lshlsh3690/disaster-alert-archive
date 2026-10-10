package com.disaster.alert.alertapi.domain.event.model;

// Red 단계 뼈대 — 구현은 Green 에서
public enum PairLayer {
    ADVISORY, INCIDENT, MIXED;

    public static PairLayer of(boolean firstAdvisory, boolean secondAdvisory) {
        return MIXED;
    }
}
