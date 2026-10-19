package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelCsv.Label;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCounter.Observation;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PairCounterTest {

    // 라벨 · 배정됨 · 같은 이벤트 여부를 한 줄로 만드는 도우미. 알림 id 는 쌍을 구분하려고 번호를 붙인다.
    private static Observation obs(long id, Label label, boolean assigned, boolean sameEvent) {
        return new Observation(id * 10, id * 10 + 1, label, assigned, sameEvent);
    }

    // 가이드 "예시 (쌍 6개)" 그대로. 과소병합율 = 2÷(2+2) = 50%, 과병합율 = 1÷(2+1) = 33.3%.
    // Red 단계: 뼈대는 모든 개수 0, 비율 empty, 틀린 쌍 없음 → correctlyMerged 기대(2)에서 실패한다.
    @Test
    void 가이드_예시_6쌍은_과소_50퍼센트_과병합_33점3퍼센트() {
        // 준비: 쌍 1~2 맞게 묶음, 3~4 놓침, 5 잘못 묶음, 6 은 어느 개수에도 안 들어감
        List<Observation> observations = List.of(
                obs(1, Label.SAME, true, true),
                obs(2, Label.SAME, true, true),
                obs(3, Label.SAME, true, false),
                obs(4, Label.SAME, true, false),
                obs(5, Label.DIFF, true, true),
                obs(6, Label.DIFF, true, false));

        // 실행
        PairCountResult result = PairCounter.count(observations);

        // 검증: 개수, 비율, 그리고 틀린 쌍은 놓침 2 + 잘못 묶음 1 만 담긴다
        assertThat(result.correctlyMerged()).isEqualTo(2);
        assertThat(result.missed()).isEqualTo(2);
        assertThat(result.overMerged()).isEqualTo(1);
        assertThat(result.underMergeRate().getAsDouble()).isEqualTo(50.0);
        assertThat(result.overMergeRate().getAsDouble()).isCloseTo(33.3333, within(0.001));
        assertThat(result.wrongPairs()).containsExactlyInAnyOrder(
                new WrongPair(30, 31, WrongKind.MISSED),
                new WrongPair(40, 41, WrongKind.MISSED),
                new WrongPair(50, 51, WrongKind.OVER_MERGED));
    }

    // 분모가 0 이면 0% 가 아니라 "비율 없음" 이어야 한다(0% 는 "아주 좋음" 으로 오해된다).
    // 같은 사건 라벨이 하나도 없고, 묶인 쌍도 없는 입력: 두 비율 모두 empty. 제외 개수도 함께 확인해
    // 뼈대(전부 0)로는 통과하지 못하게 한다.
    // Red 단계: 비율은 뼈대도 empty 라 맞지만 excludedUnsure 가 0 으로 나와 실패한다.
    @Test
    void 분모가_0이면_비율이_없다() {
        // 준비: DIFF+다른 이벤트(어디에도 안 셈) 1쌍과 UNSURE 1쌍뿐
        List<Observation> observations = List.of(
                obs(1, Label.DIFF, true, false),
                obs(2, Label.UNSURE, true, true));

        // 실행
        PairCountResult result = PairCounter.count(observations);

        // 검증: 두 분모(맞게 묶음+놓침, 맞게 묶음+잘못 묶음)가 모두 0 → 비율 empty
        assertThat(result.underMergeRate()).isEmpty();
        assertThat(result.overMergeRate()).isEmpty();
        assertThat(result.excludedUnsure()).isEqualTo(1);
        // DIFF+다른 이벤트 쌍은 맞게 묶음·놓침·잘못 묶음·미배정 어디에도 들어가지 않는다
        assertThat(result.correctlyMerged() + result.missed() + result.overMerged()
                + result.excludedUnassigned()).isZero();
    }

    // 잘못 묶음만 있고 같은 사건 라벨이 없으면 과소병합율은 분모 0(비율 없음)인데 과병합율은 100% 로 계산돼야 한다.
    // 한쪽 분모만 0 일 때 다른 쪽이 같이 사라지지 않는지 고정한다.
    // Red 단계: 뼈대는 과병합율도 empty 를 돌려줘 getAsDouble 에서 NoSuchElementException 으로 실패한다.
    @Test
    void 한쪽_분모만_0이면_그쪽만_비율이_없다() {
        // 준비: 다른 사건인데 같은 이벤트로 묶인 쌍 1개
        List<Observation> observations = List.of(obs(1, Label.DIFF, true, true));

        // 실행
        PairCountResult result = PairCounter.count(observations);

        // 검증: 과소병합율 분모 = 맞게 묶음(0)+놓침(0) = 0 → empty, 과병합율 = 1÷(0+1) = 100%
        assertThat(result.underMergeRate()).isEmpty();
        assertThat(result.overMergeRate().getAsDouble()).isEqualTo(100.0);
    }

    // UNSURE 이면서 미배정인 쌍은 "모르겠음" 으로만 센다(가이드: 둘 다 해당하면 모르겠음). 이중 집계하면
    // 두 제외 건수의 합이 전체 쌍 수보다 커져 리포트를 믿을 수 없게 된다.
    // 미배정(SAME) 쌍은 정답을 가릴 수 없어 비율 분모에서 빠지고 미배정 건수로만 센다.
    // Red 단계: 뼈대는 제외 개수가 전부 0 이라 excludedUnsure 기대(1)에서 실패한다.
    @Test
    void UNSURE_미배정은_모르겠음으로만_세고_미배정은_비율에서_빠진다() {
        // 준비: UNSURE+미배정 1쌍, SAME+미배정 1쌍(놓침으로 오인되기 쉬움), SAME 맞게 묶음 1쌍
        List<Observation> observations = List.of(
                obs(1, Label.UNSURE, false, false),
                obs(2, Label.SAME, false, false),
                obs(3, Label.SAME, true, true));

        // 실행
        PairCountResult result = PairCounter.count(observations);

        // 검증: 모르겠음 1, 미배정 1(UNSURE 쌍은 미배정에 안 셈), 놓침 0, 과소병합율 = 0÷(1+0) = 0%
        assertThat(result.excludedUnsure()).isEqualTo(1);
        assertThat(result.excludedUnassigned()).isEqualTo(1);
        assertThat(result.missed()).isZero();
        assertThat(result.underMergeRate().getAsDouble()).isEqualTo(0.0);
    }
}
