package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.GradedPair;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.VersionGrade;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.WrongSide;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 불일치 쌍 사람 라벨 순수 판정·집계 테스트. DB·Spring 없이 도는 단위 테스트다.
 */
class DisagreementGradingTest {

    /**
     * 사람 라벨을 정답으로 보고, 판사와 시스템 중 누가 틀렸는지 가려야 한다. 불일치 쌍은 판사와 시스템의 답이 서로 반대라
     * 라벨이 SAME/DIFF 면 정확히 한쪽만 틀린다. "모르겠음"(UNSURE)은 정답이 없으므로 누구도 틀렸다고 하지 않는다(null).
     * Red 단계에서는 뼈대가 항상 null 을 돌려줘서, 첫 단언(판사가 틀림 → JUDGE)에서 실패한다.
     */
    @Test
    void 사람_라벨과_다른_답을_낸_쪽이_틀린_쪽이고_모르겠음은_판정이_없다() {
        // 실행·검증 1: 사람 "같은 사건", 판사 "다른 사건", 시스템은 같은 이벤트에 묶음(과병합) → 사람과 어긋난 건 판사
        assertThat(DisagreementGrading.wrongSide(Label.SAME, JudgeVerdict.DIFF, true))
                .isEqualTo(WrongSide.JUDGE);

        // 실행·검증 2: 사람 "같은 사건", 판사도 "같은 사건", 시스템은 다른 이벤트로 가름(과소병합) → 어긋난 건 시스템
        assertThat(DisagreementGrading.wrongSide(Label.SAME, JudgeVerdict.SAME, false))
                .isEqualTo(WrongSide.SYSTEM);

        // 실행·검증 3: 사람이 모르겠다고 한 쌍은 정답이 없어 판정하지 않는다 — 판사·시스템 값이 무엇이든 null
        assertThat(DisagreementGrading.wrongSide(Label.UNSURE, JudgeVerdict.SAME, false)).isNull();
    }

    /**
     * 판사 버전별로 "판사 맞음 / 시스템 맞음"을 센다. 모르겠음 쌍은 분모에서 빼고 개수만 따로 남긴다 — 정답 없는 쌍을
     * 맞음·틀림에 넣으면 비율이 왜곡된다. (spec 스토리 2 예시: 같은 사건 3쌍[판사 SAME 2·DIFF 1], 다른 사건 1쌍[판사 SAME],
     * 모르겠음 1쌍 → 판사 2/4, 시스템 2/4, 모르겠음 1.)
     * Red 단계에서는 뼈대가 빈 목록을 돌려줘서, v2 한 줄이 나온다는 단언에서 실패한다.
     */
    @Test
    void 버전별로_판사_맞음과_시스템_맞음을_세고_모르겠음은_분모에서_뺀다() {
        // 준비: 모두 판사 버전 v2 로 채점된 불일치 쌍. 불일치 쌍이라 시스템은 항상 판사와 반대로 답했다
        List<GradedPair> pairs = List.of(
                // 사람 같은 사건 + 판사 같은 사건 2쌍 → 판사 맞음, 시스템은 갈라놨으니(false) 틀림
                new GradedPair("v2", Label.SAME, JudgeVerdict.SAME, false),
                new GradedPair("v2", Label.SAME, JudgeVerdict.SAME, false),
                // 사람 같은 사건 + 판사 다른 사건 1쌍 → 판사 틀림, 시스템은 묶었으니(true) 맞음
                new GradedPair("v2", Label.SAME, JudgeVerdict.DIFF, true),
                // 사람 다른 사건 + 판사 같은 사건 1쌍 → 판사 틀림, 시스템은 갈라놨으니(false) 맞음
                new GradedPair("v2", Label.DIFF, JudgeVerdict.SAME, false),
                // 사람 모르겠음 1쌍 → 맞음·틀림 어디에도 넣지 않고 unsureCount 로만 센다
                new GradedPair("v2", Label.UNSURE, JudgeVerdict.SAME, false));

        // 실행: 버전별 집계
        List<VersionGrade> grades = DisagreementGrading.gradeByVersion(pairs);

        // 검증: v2 한 줄. 분모는 모르겠음을 뺀 4쌍, 판사·시스템 모두 2쌍 맞음, 모르겠음 1쌍
        assertThat(grades).hasSize(1);
        VersionGrade v2 = grades.get(0);
        assertThat(v2.version()).isEqualTo("v2");
        assertThat(v2.judgeRight().numerator()).isEqualTo(2);
        assertThat(v2.judgeRight().denominator()).isEqualTo(4);
        assertThat(v2.systemRight().numerator()).isEqualTo(2);
        assertThat(v2.systemRight().denominator()).isEqualTo(4);
        assertThat(v2.unsureCount()).isEqualTo(1);
    }
}
