package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.JudgeAgreementCounter.PairRecord;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.Agreement;
import com.disaster.alert.alertapi.domain.event.judge.ClusteringJudge.JudgeResult;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeAgreementCounterTest {

    // 사람 라벨과 판사 판정(토큰은 이 테스트의 관심사가 아니라 0)을 한 줄로 만드는 도우미.
    // 집계는 두 알림을 쓰지 않아 비워 둔다(리포트 목록에서만 쓴다)
    private static PairRecord judged(Label human, JudgeVerdict verdict) {
        return judged(human, new JudgeResult(verdict, 0, 0));
    }

    private static PairRecord judged(Label human, JudgeResult result) {
        return new PairRecord(human, result, null, null);
    }

    // 일치율 분모는 "판사가 SAME/DIFF 로 답한 쌍" 뿐이어야 한다. UNSURE·형식 실패를 분모에 넣으면 판사가 모른다고 한 쌍이
    // 전부 "불일치" 로 읽혀 일치율이 깎이고, 반대로 일치로 세면 부풀려진다. 둘은 분모에서 빼고 개수를 따로 남긴다.
    // Red 단계: 뼈대는 일치율 0/0, 개수 0 을 돌려줘서 sameNum 기대(2)에서 실패한다.
    @Test
    void 일치율_분모는_판사가_SAME_DIFF로_답한_쌍만_UNSURE와_형식실패는_따로_센다() {
        // 준비: 사람 SAME 5쌍 — 판사 SAME 2(일치), DIFF 1(불일치), UNSURE 1, 형식 실패 1
        //       사람 DIFF 3쌍 — 판사 DIFF 1(일치), SAME 1(불일치), UNSURE 1
        List<PairRecord> input = List.of(
                judged(Label.SAME, JudgeVerdict.SAME),
                judged(Label.SAME, JudgeVerdict.SAME),
                judged(Label.SAME, JudgeVerdict.DIFF),
                judged(Label.SAME, JudgeVerdict.UNSURE),
                judged(Label.SAME, JudgeVerdict.FORMAT_FAILURE),
                judged(Label.DIFF, JudgeVerdict.DIFF),
                judged(Label.DIFF, JudgeVerdict.SAME),
                judged(Label.DIFF, JudgeVerdict.UNSURE));

        // 실행
        JudgeAgreementResult result = JudgeAgreementCounter.count(input);

        // 검증: 같은 사건 쌍은 5쌍 중 UNSURE·형식 실패 2쌍을 뺀 3쌍이 분모, 그중 2쌍이 일치.
        //       다른 사건 쌍은 3쌍 중 UNSURE 1쌍을 뺀 2쌍이 분모, 그중 1쌍이 일치.
        assertThat(result.agreement()).isEqualTo(new Agreement(2, 3, 1, 2));
        // 빠진 쌍은 사라지지 않고 개수로 남는다: 판사 UNSURE 는 사람 SAME·DIFF 쪽 합쳐 2, 형식 실패는 1
        assertThat(result.judgeUnsure()).isEqualTo(2);
        assertThat(result.formatFailures()).isEqualTo(1);
    }

    // 교차표는 사람 라벨 행 × 판사 판정 열의 개수다. 사람 라벨 UNSURE 쌍은 측정 대상이 아니므로 어느 칸에도,
    // 어느 합계에도 들어오지 않아야 한다(들어오면 사람이 못 정한 쌍으로 판사를 채점하게 된다).
    // Red 단계: 뼈대의 교차표는 비어 있어 모든 칸이 0 → (SAME, SAME) 기대(2)에서 실패한다.
    @Test
    void 교차표는_사람_라벨_행과_판사_판정_열을_세고_사람_UNSURE_쌍은_무시한다() {
        // 준비: 사람 SAME 에 판사 SAME 2·DIFF 1·형식 실패 1, 사람 DIFF 에 판사 SAME 1,
        //       그리고 사람 UNSURE 쌍 1개(판사가 SAME 이라 답했어도 집계에 들어오면 안 됨)
        List<PairRecord> input = List.of(
                judged(Label.SAME, JudgeVerdict.SAME),
                judged(Label.SAME, JudgeVerdict.SAME),
                judged(Label.SAME, JudgeVerdict.DIFF),
                judged(Label.SAME, JudgeVerdict.FORMAT_FAILURE),
                judged(Label.DIFF, JudgeVerdict.SAME),
                judged(Label.UNSURE, JudgeVerdict.SAME));

        // 실행
        JudgeAgreementResult result = JudgeAgreementCounter.count(input);

        // 검증: 칸별 개수. 형식 실패도 별도 열이라 UNSURE 열과 섞이지 않는다.
        assertThat(result.cell(Label.SAME, JudgeVerdict.SAME)).isEqualTo(2);
        assertThat(result.cell(Label.SAME, JudgeVerdict.DIFF)).isEqualTo(1);
        assertThat(result.cell(Label.SAME, JudgeVerdict.UNSURE)).isZero();
        assertThat(result.cell(Label.SAME, JudgeVerdict.FORMAT_FAILURE)).isEqualTo(1);
        assertThat(result.cell(Label.DIFF, JudgeVerdict.SAME)).isEqualTo(1);
        // 사람 UNSURE 쌍의 판정(SAME)이 일치율 분자·분모에도 새지 않는다: 같은 사건 분모 3(SAME 2 + DIFF 1), 다른 사건 분모 1
        assertThat(result.agreement()).isEqualTo(new Agreement(2, 3, 0, 1));
    }

    // 토큰 합은 비용 확인용이라 형식 실패 판정도 포함해야 한다 — 형식 실패도 호출은 했고 요금은 나갔다.
    // 사람 UNSURE 쌍은 실제 실행에서 판정하지 않으므로 합계에 넣지 않는다.
    // Red 단계: 뼈대는 토큰 합 0 → 프롬프트 합 기대(1000 + 800 + 300)에서 실패한다.
    @Test
    void 토큰은_형식_실패를_포함한_모든_판정의_합이고_사람_UNSURE_쌍은_뺀다() {
        // 준비: 정상 판정 2건, 형식 실패 1건, 사람 UNSURE 쌍 1건(합계에서 빠져야 함)
        List<PairRecord> input = List.of(
                judged(Label.SAME, new JudgeResult(JudgeVerdict.SAME, 1000, 1)),
                judged(Label.DIFF, new JudgeResult(JudgeVerdict.DIFF, 800, 1)),
                judged(Label.SAME, new JudgeResult(JudgeVerdict.FORMAT_FAILURE, 300, 5)),
                judged(Label.UNSURE, new JudgeResult(JudgeVerdict.SAME, 9999, 9)));

        // 실행
        JudgeAgreementResult result = JudgeAgreementCounter.count(input);

        // 검증: 프롬프트 1000+800+300, 완성 1+1+5(형식 실패의 5토큰 포함)
        assertThat(result.promptTokens()).isEqualTo(2100);
        assertThat(result.completionTokens()).isEqualTo(7);
    }
}
