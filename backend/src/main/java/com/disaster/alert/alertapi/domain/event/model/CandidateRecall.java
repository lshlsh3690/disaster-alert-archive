package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

// Red 단계 뼈대 — 구현은 Green 에서
public final class CandidateRecall {

    private CandidateRecall() {
    }

    public record Case(Set<Long> goldEventIds, List<Long> rankedCandidateIds) {
    }

    public static OptionalDouble recallAtK(List<Case> cases, int k) {
        return OptionalDouble.empty();
    }
}
