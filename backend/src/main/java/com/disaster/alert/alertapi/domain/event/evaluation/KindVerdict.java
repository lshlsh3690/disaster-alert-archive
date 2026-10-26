package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 판사(LLM)가 알림 한 건을 보고 내리는 분류 판정. INCIDENT = 사건, ADVISORY = 비사건(특보·안내 포함).
 * 분류기의 AlertKind 와 달리 MISSING_PERSON 을 받지 않는다 — 판사는 두 단어만 쓴다.
 */
public enum KindVerdict {
    INCIDENT,
    ADVISORY;

    /**
     * 모델 답을 판정으로 변환한다. 판정 불가면 null.
     * 앞뒤 공백·대소문자만 무시하고 한 단어가 정확히 일치할 때만 읽는다(설계안 ③).
     */
    public static KindVerdict fromModelAnswer(String answer) {
        // 1) 답이 없으면 판정 불가
        if (answer == null) {
            return null;
        }
        // 2) 앞뒤 공백·대소문자만 무시하고 enum 이름과 정확히 같을 때만 받는다.
        //    contains/startsWith 로 읽으면 "NOT SAME" 같은 부정문의 뜻이 뒤집혀 신뢰도 숫자가 오염된다.
        String token = answer.trim();
        for (KindVerdict v : values()) {
            if (v.name().equalsIgnoreCase(token)) {
                return v;
            }
        }
        // 3) 약속한 단어가 아니면(여러 단어·마침표·한국어 풀이 포함) 추측하지 않는다
        return null;
    }
}
