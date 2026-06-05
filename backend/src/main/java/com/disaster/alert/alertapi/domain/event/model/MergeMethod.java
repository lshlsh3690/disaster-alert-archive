package com.disaster.alert.alertapi.domain.event.model;

/**
 * 매핑 행이 어떤 방식으로 이벤트에 합류했는지 표식 (event_alert_mapping.merge_method).
 *
 * <p>감사/튜닝용 — 특히 가장 위험한 {@link #LLM} 머지를 사후 검수/되돌리기 위해 박제한다.
 * (코사인 유사도는 임베딩에서 재계산되지만, LLM 판정은 일회성이라 재구성 불가.)
 */
public enum MergeMethod {
    /** 이벤트 첫 알림 (sequence_no=1). 비교 대상 없음. */
    SEED,
    /** local 코사인 유사도 머지 (지역 교집합 + 임베딩 ≥ threshold). */
    EMBEDDING,
    /** 광역 broadcast 머지 (시도+유형 또는 전국 키, 임베딩 미사용). */
    BROADCAST,
    /** cross-region 인물 결정적 매칭 머지 (이름+나이+키 일치, LLM 미사용). */
    IDENTITY,
    /** cross-region LLM 판정 머지 (동물·비정형 — 정형 키가 없어 LLM 으로 판정). */
    LLM
}
