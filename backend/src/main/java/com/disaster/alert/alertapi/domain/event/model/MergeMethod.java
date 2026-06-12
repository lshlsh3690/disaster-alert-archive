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
    LLM,
    /**
     * local borderline LLM 폴백 머지 — 같은 지역 후보지만 임베딩이 임계(0.85) 미만이라
     * 임베딩으로는 못 묶은 사고성 사건(서소문 고가 붕괴처럼 같은 사건의 다른 측면 안내)을
     * LLM 판정으로 묶는다. cross-region({@link #LLM})과 달리 같은 시군구 안에서 일어난다.
     */
    LLM_FALLBACK,
    /**
     * 전국 통합 유형 머지 — 태풍처럼 본질적으로 전국 단일 사건인 유형을 지역·임베딩 무관하게
     * 시간 윈도우(기본 7일)로만 하나의 broadcast 이벤트에 묶는다. 태풍 알림이 시군구 단위로
     * 쪼개져 발송돼도({@link #BROADCAST} span 게이트를 못 넘어도) 단일 사건으로 통합.
     * {@link MissingPersonIdentity} 인물처럼 유형 자체가 키라 임베딩을 보지 않는다.
     */
    GLOBAL_TYPE
}
