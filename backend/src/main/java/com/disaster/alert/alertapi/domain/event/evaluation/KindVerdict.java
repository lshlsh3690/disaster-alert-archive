package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 판사(LLM)가 알림 한 건을 보고 내리는 분류 판정. INCIDENT = 사건, ADVISORY = 비사건(특보·안내 포함).
 * 분류기의 AlertKind 와 달리 MISSING_PERSON 을 받지 않는다 — 판사는 두 단어만 쓴다.
 */
public enum KindVerdict {
    INCIDENT,
    ADVISORY;

    // Red 단계 뼈대 — 구현은 Green 에서
    /**
     * 모델 답을 판정으로 변환한다. 판정 불가면 null.
     * 앞뒤 공백·대소문자만 무시하고 한 단어가 정확히 일치할 때만 읽는다(설계안 ③).
     */
    public static KindVerdict fromModelAnswer(String answer) {
        return null;
    }
}
