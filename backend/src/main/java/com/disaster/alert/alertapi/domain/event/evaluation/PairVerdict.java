package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 판사(LLM)가 두 알림을 보고 내리는 쌍 판정. SAME = 같은 사건, DIFF = 다른 사건.
 */
public enum PairVerdict {
    SAME,
    DIFF;

    // Red 단계 뼈대 — 구현은 Green 에서
    /**
     * 모델 답을 판정으로 변환한다. 판정 불가면 null.
     * 앞뒤 공백·대소문자만 무시하고 한 단어가 정확히 일치할 때만 읽는다(설계안 ③).
     */
    public static PairVerdict fromModelAnswer(String answer) {
        return null;
    }
}
