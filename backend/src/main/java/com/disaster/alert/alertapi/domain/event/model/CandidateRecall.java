package com.disaster.alert.alertapi.domain.event.model;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/** 후보 검색이 정답 기존 사건을 상위 k 안에 올렸는지(recall@k). */
public final class CandidateRecall {

    private CandidateRecall() {
    }

    public record Case(Set<Long> goldEventIds, List<Long> rankedCandidateIds) {
    }

    public static OptionalDouble recallAtK(List<Case> cases, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k 는 1 이상이어야 합니다: " + k);
        }
        // 정답 기존 사건이 없는(신규 사건) Case 는 후보 검색으로 맞출 대상이 아니므로 분모에서 뺀다.
        List<Case> scorable = cases.stream().filter(c -> !c.goldEventIds().isEmpty()).toList();
        if (scorable.isEmpty()) {
            return OptionalDouble.empty();
        }
        long hits = scorable.stream()
                .filter(c -> c.rankedCandidateIds().stream().limit(k).anyMatch(c.goldEventIds()::contains))
                .count();
        return OptionalDouble.of((double) hits / scorable.size());
    }
}
