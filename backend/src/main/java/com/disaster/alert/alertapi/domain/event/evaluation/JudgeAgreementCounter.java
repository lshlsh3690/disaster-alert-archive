package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * (사람 라벨, 판사 판정) 목록을 세어 교차표·일치율·형식 실패 수·토큰 합을 만든다.
 * 판사 호출·DB 조회는 하지 않는 순수 집계다(호출은 JudgeAgreementRunner 몫).
 */
// Red 단계 뼈대 — 구현은 Green 에서
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeAgreementCounter {

    /** 한 쌍의 사람 라벨과 그 쌍에 대한 판사 결과. */
    public record Judged(Label humanLabel, JudgeResult result) {
    }

    public static JudgeAgreementResult count(List<Judged> judged) {
        return new JudgeAgreementResult(Map.of(), new Agreement(0, 0, 0, 0), 0, 0, 0, 0);
    }
}
