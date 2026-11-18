package com.disaster.alert.alertapi.domain.event.dashboard;

import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.AlertInfo;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.ExportItem;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.GradedPair;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.Kind;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.LabeledDisagreement;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.VersionGrade;
import com.disaster.alert.alertapi.domain.event.dashboard.DisagreementGrading.WrongSide;
import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.judge.JudgeVerdict;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * 내보내기 파일은 평가 도구(LabelJson)가 읽는데, 그 도구는 같은 쌍이 두 번 있으면 오류로 거절한다. 같은 두 알림이 두 날
     * 불일치로 나왔어도 항목은 한 번만, 가장 최근 날짜 기준으로 넣어야 한다. 번호 q 는 1부터 빈틈없이 매긴다.
     * Red 단계에서는 뼈대가 빈 목록을 돌려줘서, 항목이 2개라는 첫 단언에서 실패한다.
     */
    @Test
    void 같은_쌍이_두_날짜에_있으면_가장_최근_날짜로_한_번만_내보내고_q는_1부터다() {
        // 준비: (101,205) 쌍이 11-18 과 11-19 두 날 불일치로 나왔고, 다른 쌍 (150,300) 은 11-17 에 한 번
        LocalDate criteria = LocalDate.of(2026, 11, 13);
        List<LabeledDisagreement> labeled = List.of(
                // 오래된 날짜를 앞에 둬서 "첫 항목을 그냥 쓰는" 구현을 걸러낸다
                new LabeledDisagreement(101, 205, Label.DIFF, criteria, LocalDate.of(2026, 11, 18),
                        Kind.OVER_MERGED, JudgeVerdict.DIFF),
                new LabeledDisagreement(101, 205, Label.DIFF, criteria, LocalDate.of(2026, 11, 19),
                        Kind.OVER_MERGED, JudgeVerdict.DIFF),
                new LabeledDisagreement(150, 300, Label.SAME, criteria, LocalDate.of(2026, 11, 17),
                        Kind.MISSED, JudgeVerdict.SAME));
        Map<Long, AlertInfo> alerts = Map.of(
                101L, new AlertInfo(101, LocalDateTime.of(2026, 11, 19, 9, 0), "호우", "첫째 원문"),
                205L, new AlertInfo(205, LocalDateTime.of(2026, 11, 19, 12, 0), "호우", "둘째 원문"));

        // 실행: 항목 조립
        List<ExportItem> items = DisagreementGrading.exportItems(labeled, alerts);

        // 검증: 세 건이 아니라 두 쌍. 번호는 1, 2 (순서는 정하지 않는다 — 중복 제거와 번호 매기기만 고정)
        assertThat(items).hasSize(2);
        assertThat(items).extracting(ExportItem::q).containsExactlyInAnyOrder(1, 2);
        // 검증: 중복된 쌍은 한 항목이고 가장 최근 날짜(11-19)가 남는다
        assertThat(items).filteredOn(item -> item.a().id() == 101)
                .singleElement()
                .extracting(ExportItem::scoreDate)
                .isEqualTo(LocalDate.of(2026, 11, 19));
    }
}
