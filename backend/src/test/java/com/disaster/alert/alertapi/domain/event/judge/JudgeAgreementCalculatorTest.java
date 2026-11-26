package com.disaster.alert.alertapi.domain.event.judge;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.PairLabel;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.PairVerdict;
import com.disaster.alert.alertapi.domain.event.judge.JudgeAgreementCalculator.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 판사 일치율 계산의 순수 로직 테스트. 판사(LLM)는 부르지 않고, 저장된 판정과 현재 사람 라벨만 맞춰 센다.
 * DB·Spring 없이 도는 단위 테스트다.
 */
class JudgeAgreementCalculatorTest {

    /**
     * 결정: 같은 사건·다른 사건 일치율은 "사람 라벨이 그 값인 쌍" 중 "판사가 같은 값을 낸 쌍"의 비율이다.
     * 판정과 라벨은 따로 저장돼 알림 순서가 서로 반대일 수 있으므로 (작은 id, 큰 id) 로 맞춰 같은 쌍으로 본다.
     * Red 단계에서는 뼈대가 전부 0 을 돌려줘서 분자·분모 기대값(2/3, 1/2)과 다르다.
     */
    @Test
    void 현재_라벨로_같은_사건_다른_사건_분자_분모를_센다() {
        // 준비: 라벨 SAME 3쌍(1-2, 3-4, 5-6), DIFF 2쌍(7-8, 9-10). 3-4 는 라벨 쪽을 일부러 4-3 으로 뒤집어 저장한다.
        List<PairLabel> labels = List.of(
                new PairLabel(1, 2, Label.SAME),
                new PairLabel(4, 3, Label.SAME),
                new PairLabel(5, 6, Label.SAME),
                new PairLabel(7, 8, Label.DIFF),
                new PairLabel(9, 10, Label.DIFF));
        // 판사: SAME 라벨 중 1-2 맞힘, 3-4 맞힘(순서 반대), 5-6 틀림(DIFF) / DIFF 라벨 중 7-8 맞힘, 9-10 틀림(SAME)
        List<PairVerdict> verdicts = List.of(
                new PairVerdict(1, 2, RecordedVerdict.SAME),
                new PairVerdict(3, 4, RecordedVerdict.SAME),
                new PairVerdict(5, 6, RecordedVerdict.DIFF),
                new PairVerdict(7, 8, RecordedVerdict.DIFF),
                new PairVerdict(9, 10, RecordedVerdict.SAME));

        // 실행
        Result result = JudgeAgreementCalculator.calculate(verdicts, labels);

        // 검증: 같은 사건은 3쌍 중 2쌍 일치, 다른 사건은 2쌍 중 1쌍 일치. 순서가 뒤집힌 3-4 가 빠지면 분모가 2 가 된다.
        assertThat(result.sameNum()).isEqualTo(2);
        assertThat(result.sameDen()).isEqualTo(3);
        assertThat(result.diffNum()).isEqualTo(1);
        assertThat(result.diffDen()).isEqualTo(2);
    }

    /**
     * 결정: 사람 라벨은 측정 때 값이 아니라 계산 때의 현재 값을 쓴다. 그래야 라벨을 고친 뒤 판사를 다시 부르지 않고
     * 같은 판정으로 일치율이 바로 갱신된다.
     * Red 단계에서는 뼈대가 두 벌 모두 0 을 돌려줘서 "분모가 옮겨 간다"는 기대와 다르다.
     */
    @Test
    void 라벨이_SAME_에서_DIFF_로_바뀌면_그_쌍이_다른_사건_분모로_옮겨_간다() {
        // 준비: 판정은 고정. 1-2 는 판사가 SAME 이라고 했다.
        List<PairVerdict> verdicts = List.of(new PairVerdict(1, 2, RecordedVerdict.SAME));

        // 실행: 같은 판정을 라벨 두 벌(라벨 정정 전 SAME / 정정 후 DIFF)로 각각 계산한다.
        Result before = JudgeAgreementCalculator.calculate(verdicts, List.of(new PairLabel(1, 2, Label.SAME)));
        Result after = JudgeAgreementCalculator.calculate(verdicts, List.of(new PairLabel(1, 2, Label.DIFF)));

        // 검증: 정정 전엔 같은 사건 1/1 일치. 정정 후엔 같은 사건 분모에서 빠지고 다른 사건 분모 1 로 가며, 판사가 SAME 이라 불일치(분자 0).
        assertThat(before.sameNum()).isEqualTo(1);
        assertThat(before.sameDen()).isEqualTo(1);
        assertThat(before.diffDen()).isZero();
        assertThat(after.sameDen()).isZero();
        assertThat(after.diffNum()).isZero();
        assertThat(after.diffDen()).isEqualTo(1);
    }

    /**
     * 결정: 사람 라벨은 있는데 이번 측정에 판정이 없는 쌍은 "틀린 것"이 아니라 "측정 안 한 것"이다. 분모에 넣으면
     * 일치율이 부당하게 낮아지므로 분모 밖에서 unmeasuredPairs 로 따로 센다(라벨을 새로 붙인 쌍이 대표 사례).
     * Red 단계에서는 뼈대가 0 을 돌려줘서 unmeasuredPairs 기대값 1 과 다르다.
     */
    @Test
    void 라벨은_있는데_판정이_없는_쌍은_분모에_넣지_않고_측정_안_된_쌍으로_센다() {
        // 준비: 라벨 SAME 2쌍(1-2, 3-4) 중 1-2 만 판정이 있다. 3-4 는 이번 측정에 없다.
        List<PairLabel> labels = List.of(
                new PairLabel(1, 2, Label.SAME),
                new PairLabel(3, 4, Label.SAME));
        List<PairVerdict> verdicts = List.of(new PairVerdict(1, 2, RecordedVerdict.SAME));

        // 실행
        Result result = JudgeAgreementCalculator.calculate(verdicts, labels);

        // 검증: 분모는 판정이 있는 1-2 하나뿐(1/1), 판정 없는 3-4 는 unmeasuredPairs 1.
        assertThat(result.sameNum()).isEqualTo(1);
        assertThat(result.sameDen()).isEqualTo(1);
        assertThat(result.unmeasuredPairs()).isEqualTo(1);
    }

    /**
     * 결정: 판사가 UNSURE·형식 실패·호출 실패를 낸 쌍은 같다/다르다 답을 낸 게 아니므로 분모에서 빼고, 원인별로
     * 따로 센다. 섞어서 세면 "판사가 모른다"와 "API 가 죽었다"를 구분할 수 없다.
     * Red 단계에서는 뼈대가 0 을 돌려줘서 각 건수 기대값 1 과 다르다.
     */
    @Test
    void 판사의_UNSURE_형식실패_호출실패는_분모_밖에서_각각_센다() {
        // 준비: 라벨 SAME 3쌍, 판정은 각각 UNSURE / FORMAT_FAILURE / CALL_FAILURE.
        List<PairLabel> labels = List.of(
                new PairLabel(1, 2, Label.SAME),
                new PairLabel(3, 4, Label.SAME),
                new PairLabel(5, 6, Label.SAME));
        List<PairVerdict> verdicts = List.of(
                new PairVerdict(1, 2, RecordedVerdict.UNSURE),
                new PairVerdict(3, 4, RecordedVerdict.FORMAT_FAILURE),
                new PairVerdict(5, 6, RecordedVerdict.CALL_FAILURE));

        // 실행
        Result result = JudgeAgreementCalculator.calculate(verdicts, labels);

        // 검증: 세 쌍 모두 분모 0, 원인별 건수가 1 씩.
        assertThat(result.sameDen()).isZero();
        assertThat(result.judgeUnsure()).isEqualTo(1);
        assertThat(result.formatFailures()).isEqualTo(1);
        assertThat(result.callFailures()).isEqualTo(1);
    }
}
