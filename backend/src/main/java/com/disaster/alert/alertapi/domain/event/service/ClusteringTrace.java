package com.disaster.alert.alertapi.domain.event.service;

/**
 * 알림 1건을 클러스터링할 때 판정 코드가 실제로 본 값(clustering_trace 한 행). 값을 옮겨 담을 뿐 원인 추정은 없다.
 * recorded_at 은 기록하는 쪽(DB)이 채우므로 여기에 두지 않는다.
 * 컬럼 명세: docs/CLUSTERING_EVAL_GUIDE.md "판정 기록(trace)".
 *
 * @param candidates         같은 시군구 후보 "이벤트id:거리, ..." (예 "12:0.2100, 30:0.3300"). 임베딩 경로가 아니면 null
 * @param adjacentCandidates 인접 시군구 후보(같은 형식). 조회하지 않았으면 null
 * @param threshold          적용된 병합 기준 거리. 임베딩 경로가 아니면 null
 * @param llm                호출 안 함 null / "후보 n건 → event X" / "후보 n건 → 없음"
 * @param eventId            최종 배정 이벤트 id (SKIPPED 면 null)
 * @param note               한 줄 사실. 추정 금지
 */
// Red 단계 뼈대 — 구현은 Green 에서
public record ClusteringTrace(
        long alertId,
        Path path,
        String candidates,
        String adjacentCandidates,
        Double threshold,
        String llm,
        Result result,
        Long eventId,
        String note
) {

    public enum Path {
        ADVISORY_SKIPPED, PERSON, GLOBAL_TYPE, REGIONAL_TYPE, EPISODIC, BROADCAST, NO_REGION, EMBEDDING
    }

    public enum Result {
        MERGED, NEW, SKIPPED
    }
}
