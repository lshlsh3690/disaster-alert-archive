package com.disaster.alert.alertapi.domain.event.model;

/** 평가 쌍의 층. 안내성 문구끼리와 사건 문구끼리는 거리 분포가 달라 합산하면 서로를 가린다. */
public enum PairLayer {
    ADVISORY, INCIDENT, MIXED;

    public static PairLayer of(boolean firstAdvisory, boolean secondAdvisory) {
        if (firstAdvisory && secondAdvisory) {
            return ADVISORY;
        }
        if (!firstAdvisory && !secondAdvisory) {
            return INCIDENT;
        }
        return MIXED;
    }
}
