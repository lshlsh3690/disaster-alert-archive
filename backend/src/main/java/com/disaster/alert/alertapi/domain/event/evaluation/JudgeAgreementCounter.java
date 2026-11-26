package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * (사람 라벨, 판사 판정) 목록을 세어 교차표·일치율·형식 실패 수·토큰 합을 만든다.
 * 판사 호출·DB 조회는 하지 않는 순수 집계다(호출은 JudgeAgreementRunner 몫).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JudgeAgreementCounter {

    /**
     * 한 쌍의 사람 라벨, 판사 결과, 두 알림. 집계는 라벨과 결과만 쓴다(두 알림은 마크다운 리포트가 쓰던 것으로, 리포트는
     * 2026-11-26 에 없앴다 — 측정은 DB 에 저장되고 이 집계는 측정 도구의 콘솔 요약에만 쓰인다).
     */
    public record PairRecord(Label human, JudgeResult result, AlertSnapshot first, AlertSnapshot second) {
        public JudgeVerdict verdict() {
            return result.verdict();
        }
    }

    /**
     * 판정 목록을 세어 결과를 만든다.
     *
     * <p>사람이 UNSURE 로 둔 쌍은 정답이 없어 판사를 채점할 수 없으므로 교차표·일치율·토큰 어디에도 넣지 않는다
     * (실행에서도 그런 쌍은 판정하지 않으니 토큰 합이 실제 요금과 맞는다).
     */
    public static JudgeAgreementResult count(List<PairRecord> judged) {
        Map<Label, Map<JudgeVerdict, Integer>> crossTab = new EnumMap<>(Label.class);
        int sameNum = 0, sameDen = 0, diffNum = 0, diffDen = 0, judgeUnsure = 0, formatFailures = 0;
        long promptTokens = 0, completionTokens = 0;

        for (PairRecord j : judged) {
            Label human = j.human();
            if (human == Label.UNSURE) {
                continue; // 1) 사람이 못 정한 쌍은 채점 대상이 아니다
            }
            JudgeVerdict verdict = j.result().verdict();
            crossTab.computeIfAbsent(human, k -> new EnumMap<>(JudgeVerdict.class)).merge(verdict, 1, Integer::sum);
            // 2) 토큰은 형식 실패 판정도 호출했으니 요금이 나가서 포함한다
            promptTokens += j.result().promptTokens();
            completionTokens += j.result().completionTokens();

            // 3) 판사가 모른다고 답한 쌍·형식이 어긋난 쌍은 분모에서 빼고 개수만 따로 남긴다 — 일치/불일치 어느 쪽으로도 세면 일치율이 왜곡된다
            if (verdict == JudgeVerdict.UNSURE) {
                judgeUnsure++;
                continue;
            }
            if (verdict == JudgeVerdict.FORMAT_FAILURE) {
                formatFailures++;
                continue;
            }
            // 4) 분모는 사람 라벨 쪽(같은 사건 쌍/다른 사건 쌍), 분자는 판사가 같은 쪽으로 답한 쌍
            if (human == Label.SAME) {
                sameDen++;
                sameNum += verdict == JudgeVerdict.SAME ? 1 : 0;
            } else {
                diffDen++;
                diffNum += verdict == JudgeVerdict.DIFF ? 1 : 0;
            }
        }
        return new JudgeAgreementResult(crossTab, new Agreement(sameNum, sameDen, diffNum, diffDen),
                judgeUnsure, formatFailures, promptTokens, completionTokens);
    }
}
