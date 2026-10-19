package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.Set;

/**
 * 알림이 클러스터링에서 탄 경로. 놓친 쌍의 원인을 임베딩 후보 검색으로 설명할 수 있는지 가른다.
 */
public enum EvaluationPath {
    /** 키 병합 경로(실종자 신원·태풍·지역 한정·단일 지역 에피소드형) — 후보 검색·판정이 없다. */
    KEY,
    /** 광역(broadcast) 경로 — 임베딩 후보에 없다. */
    BROADCAST,
    /** 임베딩 후보 검색 + 판정 경로. */
    EMBEDDING;

    // Red 단계 뼈대 — 구현은 Green 에서
    public static EvaluationPath of(String disasterType, String message, int sigunguCount,
                                    Set<String> regionalTypes, Set<String> globalTypes,
                                    Set<String> episodicTypes) {
        return EMBEDDING;
    }
}
