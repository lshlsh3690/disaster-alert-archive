package com.disaster.alert.alertapi.domain.event.service;

import java.util.List;

// Red 단계 뼈대 — 구현은 Green 에서
public final class HybridCandidateRanker {

    private HybridCandidateRanker() {
    }

    public static List<Long> fuse(List<Long> vectorRanked, List<Long> keywordRanked, int k, int topN) {
        return List.of();
    }
}
