package com.disaster.alert.alertapi.domain.event.judge;

/**
 * 일치율 측정에 쌍마다 남기는 판사 결과({@code judge_agreement_run_pair.verdict}).
 *
 * <p>{@link JudgeVerdict} 네 값에 {@link #CALL_FAILURE} 를 더한 것이다. 호출 실패는 판사가 낸 답이 아니라 네트워크·API 장애라
 * {@link JudgeVerdict} 에 넣지 않고 기록용으로만 둔다. 일치율 계산에서는 UNSURE·형식 실패와 함께 분모에서 빼고 따로 센다.
 */
public enum RecordedVerdict {
    SAME,
    DIFF,
    UNSURE,
    FORMAT_FAILURE,
    /** 판사 호출 자체가 실패해 답이 없음 */
    CALL_FAILURE;

    /** 판사가 답한 판정을 기록용 값으로 바꾼다(이름이 같다). */
    public static RecordedVerdict of(JudgeVerdict verdict) {
        return valueOf(verdict.name());
    }
}
