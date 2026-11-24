package com.disaster.alert.alertapi.domain.event.evaluation;

/** 판단 없이 전부 묶거나 전부 나눌 때의 결과를 같은 평가 집합에서 만든다. */
public final class TrivialBaselines {

    private TrivialBaselines() {
    }

    // Red 단계 뼈대 — 구현은 Green 에서. 모르겠음·미배정을 제외한 쌍을 전부 묶는 기준선이다.
    public static PairCountResult allMerged(PairCountResult counts) {
        return null;
    }

    // Red 단계 뼈대 — 구현은 Green 에서. 같은 쌍을 전부 나누는 기준선이다.
    public static PairCountResult allSeparated(PairCountResult counts) {
        return null;
    }
}
