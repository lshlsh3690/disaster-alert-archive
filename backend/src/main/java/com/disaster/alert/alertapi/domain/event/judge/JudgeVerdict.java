package com.disaster.alert.alertapi.domain.event.judge;

/**
 * 판사(LLM)가 두 사건 문자를 보고 내린 판정.
 *
 * <p>{@link #UNSURE} 는 판사가 "판단 못 함"이라고 <b>답한</b> 것이고, {@link #FORMAT_FAILURE} 는 응답이 정해진 한 단어 형식이
 * 아니어서 <b>받지 못한</b> 것이다. 둘은 원인이 달라 따로 센다 — 형식 실패를 UNSURE 로 바꾸면 프롬프트·모델 문제가 "판사가 모른다"로
 * 가려진다. 점수 계산에서는 둘 다 분모에서 빼지만 개수는 각각 남긴다.
 */
public enum JudgeVerdict {
    /** 같은 사건 */
    SAME,
    /** 다른 사건 */
    DIFF,
    /** 판사가 판단하지 못한다고 답함 */
    UNSURE,
    /** 응답이 SAME/DIFF/UNSURE 한 단어가 아님 */
    FORMAT_FAILURE
}
