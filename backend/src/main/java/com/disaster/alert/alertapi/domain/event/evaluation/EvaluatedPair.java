package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 평가 대상 쌍 하나. 사람 라벨과 시스템 예측을 함께 담는다.
 *
 * @param goldEventInCandidates 한쪽 알림이 속한 이벤트가 다른 쪽 알림의 후보 목록(top-K)에 있었는가. 모르면 null.
 * @param path 쌍(나중 알림 기준)이 탄 클러스터링 경로. 키 병합·광역 경로는 임베딩 후보 검색이 해당 없다.
 * @param sharesSigungu 두 알림이 시군구를 하나라도 공유하는가. 아니면 인접 시군구 쌍이라 임베딩 후보에 없다.
 * @param assigned 두 알림 모두 이벤트에 배정됐는가. 아니면 클러스터링 대상이 아니었던 쌍이다.
 */
public record EvaluatedPair(
        long alertAId,
        long alertBId,
        EvaluationLabelType label,
        EvaluationStratum stratum,
        boolean predictedSame,
        Boolean goldEventInCandidates,
        EvaluationPath path,
        boolean sharesSigungu,
        boolean assigned) {

    /** 경로·시군구·배정 정보가 없는 기존 호출용 — 임베딩 경로, 시군구 공유, 배정됨으로 본다. */
    public EvaluatedPair(long alertAId, long alertBId, EvaluationLabelType label, EvaluationStratum stratum,
                         boolean predictedSame, Boolean goldEventInCandidates) {
        this(alertAId, alertBId, label, stratum, predictedSame, goldEventInCandidates,
                EvaluationPath.EMBEDDING, true, true);
    }
}
