package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;

import java.util.Map;

/**
 * JudgeAgreementCounter 가 세어 낸 판사 일치율 측정 결과. 측정 도구의 콘솔 요약이 이 값을 읽는다(일치율 자체는 저장한 판정과
 * 현재 라벨로 JudgeAgreementCalculator 가 다시 센다, specs/014).
 *
 * @param crossTab         사람 라벨(SAME/DIFF) × 판사 판정(4가지) 교차표. 칸 하나는 {@link #cell} 로 읽는다
 * @param agreement        같은 사건 쌍·다른 사건 쌍 일치율(분자/분모). 분모는 판사가 SAME/DIFF 로 답한 쌍만이다
 * @param judgeUnsure      판사가 UNSURE 로 답해 분모에서 빠진 쌍 수
 * @param formatFailures   응답 형식이 어긋나 분모에서 빠진 쌍 수
 * @param promptTokens     모든 판정(형식 실패 포함)의 프롬프트 토큰 합
 * @param completionTokens 모든 판정(형식 실패 포함)의 완성 토큰 합
 */
public record JudgeAgreementResult(Map<Label, Map<JudgeVerdict, Integer>> crossTab, Agreement agreement,
                                   int judgeUnsure, int formatFailures,
                                   long promptTokens, long completionTokens) {

    /** 교차표의 한 칸. 한 번도 나오지 않은 조합은 0 이다. */
    public int cell(Label human, JudgeVerdict judge) {
        return crossTab.getOrDefault(human, Map.of()).getOrDefault(judge, 0);
    }
}
