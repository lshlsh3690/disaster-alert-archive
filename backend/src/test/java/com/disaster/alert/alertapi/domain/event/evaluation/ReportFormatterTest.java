package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
import com.disaster.alert.alertapi.domain.event.evaluation.ReportFormatter.Row;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFormatterTest {

    private static final LocalDateTime RUN_AT = LocalDateTime.of(2026, 10, 19, 9, 0);

    private static PairCountResult guideExample() {
        // 가이드 예시(쌍 6개)의 결과: 맞게 묶음 2, 놓침 2, 잘못 묶음 1 → 50.0% / 33.3%
        return new PairCountResult(2, 2, 1, 0, 0, OptionalDouble.of(50.0), OptionalDouble.of(100.0 / 3),
                List.of());
    }

    // 요약은 점수 한 줄로 전체 품질을 먼저 보여 주고, 그 아래에 점수의 두 재료(맞게 묶은 비율·빠짐없이 묶은 비율)를 근거 개수와 함께 적는다.
    // 옛 과소병합율·과병합율은 괄호 안에 남겨 이전 리포트와 이어 읽게 한다.
    // Red 단계: 포매터는 아직 옛 두 줄("→ 과소병합율 ...")만 쓰므로 점수·맞게 묶은 비율·빠짐없이 묶은 비율 줄이 없어 실패한다.
    @Test
    void 요약에_점수와_두_비율_줄이_나온다() {
        // 준비: 맞게 묶음 39, 놓침 21, 잘못 묶음 3(현재 develop 측정값), 모르겠음 6쌍 제외
        // 과소병합율 = 21/60 = 35.0%, 과병합율 = 3/42 = 7.1% (PairCounter 가 이미 계산해 넘기는 값)
        PairCountResult result = new PairCountResult(39, 21, 3, 6, 0,
                OptionalDouble.of(21 * 100.0 / 60), OptionalDouble.of(3 * 100.0 / 42), List.of());

        // 실행
        String report = ReportFormatter.format("cluster-eval-2026-10", RUN_AT, result, List.of());

        // 검증: 맞게 묶은 비율 = 39/42 = 92.9%, 빠짐없이 묶은 비율 = 39/60 = 65.0%
        // 점수 = 2*39 / (2*39 + 21 + 3) = 78/102 = 76.5 (반올림된 비율이 아니라 개수로 계산한 값)
        assertThat(report)
                .contains("## 요약")
                .contains("- 점수 76.5점 — 맞게 묶은 비율과 빠짐없이 묶은 비율의 조화평균")
                .contains("- 맞게 묶은 비율 92.9% — 묶은 42쌍 중 39쌍 맞음, 3쌍 잘못 묶음 (과병합율 7.1%)")
                .contains("- 빠짐없이 묶은 비율 65.0% — 같은 사건 60쌍 중 39쌍 묶음, 21쌍 놓침 (과소병합율 35.0%)")
                .contains("- 제외: 모르겠음 6쌍, 이벤트 미배정 0쌍");
    }

    // 점수를 조화평균으로 고른 이유는 한쪽이 낮으면 점수도 끌려 내려가게 하려는 것이다(산술평균은 한쪽이 100% 면 가려진다).
    // 전부 묶어 맞게 묶은 비율만 100% 로 만들어도 빠짐없이 묶은 비율이 낮으면 점수가 높게 나오면 안 된다.
    // Red 단계: 포매터가 점수 줄을 아직 쓰지 않아 실패한다.
    @Test
    void 점수는_산술평균이_아니라_조화평균이다() {
        // 준비: 맞게 묶음 10, 놓침 10, 잘못 묶음 0 → 맞게 묶은 비율 100%, 빠짐없이 묶은 비율 50% (과소병합율 50.0, 과병합율 0.0)
        PairCountResult result = new PairCountResult(10, 10, 0, 0, 0,
                OptionalDouble.of(50.0), OptionalDouble.of(0.0), List.of());

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, result, List.of());

        // 검증: 산술평균이면 (100+50)/2 = 75.0 이지만 조화평균은 2*10 / (2*10 + 10 + 0) = 66.7
        assertThat(report)
                .contains("- 점수 66.7점")
                .doesNotContain("75.0점");
    }

    // 머리에는 어떤 데이터셋을 언제 돌렸는지만 적는다(옛 리포트와 직접 비교하지 않도록 이름과 시각이 필요).
    // Red 단계: 뼈대는 빈 문자열이라 데이터셋 이름이 없어 실패한다.
    @Test
    void 머리에_데이터셋_이름과_실행_시각이_들어간다() {
        // 실행
        String report = ReportFormatter.format("cluster-eval-2026-10", RUN_AT, guideExample(), List.of());

        // 검증: 데이터셋 이름과 실행 시각(2026-10-19 09:00)이 보인다
        assertThat(report).contains("cluster-eval-2026-10").contains("2026-10-19");
    }

    // 분모가 0 이면 0% 로 쓰면 "완벽" 으로 오해되므로 "-" 로 쓴다.
    // 요약 개편으로 옛 비율은 괄호 안("(과소병합율 -)")에 남는다. 이 테스트는 새 형식에서도 대시 규칙이 유지되는지 고정한다.
    @Test
    void 분모가_0이면_비율_자리에_대시() {
        // 준비: 비율이 둘 다 없는(empty) 결과
        PairCountResult result = new PairCountResult(0, 0, 0, 1, 0, OptionalDouble.empty(), OptionalDouble.empty(), List.of());

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, result, List.of());

        // 검증: 퍼센트 기호 없이 "-" 만 적힌다
        assertThat(report).contains("(과소병합율 -)").contains("(과병합율 -)");
    }

    // 실행 PC 의 기본 Locale 이 한국어/유럽형이면 String.format 이 소수점을 콤마로 쓸 수 있다.
    // Locale.ROOT 를 쓰는지 보려고 기본 Locale 을 독일어로 바꿔 확인한다(독일어는 콤마 소수점).
    // Red 단계: 뼈대는 빈 문자열이라 "33.3%" 가 없어 실패한다.
    @Test
    void 기본_Locale이_콤마_소수점이어도_점으로_쓴다() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            // 준비: 거리 0.1234 가 있는 틀린 쌍 1건
            Row row = new Row(1, 2, WrongKind.MISSED, new PairFacts("산불", "산불", true, 5, 0.1234));

            // 실행
            String report = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(row));

            // 검증: 비율과 거리 모두 점 소수점이고 콤마 소수점은 없다
            assertThat(report).contains("33.3%").contains("0.123").doesNotContain("33,3").doesNotContain("0,123");
        } finally {
            Locale.setDefault(original);
        }
    }

    // 표의 한 줄은 사실만 적는다: 유형(같으면 하나, 다르면 "A/B"), 같은 시군구(예/아니오), 간격, 본문 거리.
    // 임베딩이 없으면 거리 자리는 "-" 다(0 으로 쓰면 "아주 비슷" 으로 오해된다).
    // Red 단계: 뼈대는 빈 문자열이라 표 행이 하나도 없어 실패한다.
    @Test
    void 표_행에_유형은_같으면_하나_다르면_슬래시_거리_없으면_대시() {
        // 준비: 유형이 같은 쌍(임베딩 있음)과 유형이 다른 쌍(임베딩 없음, 시군구 불일치)
        Row sameType = new Row(101, 102, WrongKind.MISSED, new PairFacts("산불", "산불", true, 5, 0.2));
        Row diffType = new Row(201, 202, WrongKind.OVER_MERGED, new PairFacts("산불", "호우", false, 30, null));

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(sameType, diffType));

        // 검증: 헤더와 두 행. 같은 유형은 "산불" 하나, 다른 유형은 "산불/호우", 거리 없으면 "-"
        assertThat(report)
                .contains("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |")
                .contains("| 101 | 102 | 산불 | 예 | 5 |")
                .contains("| 201 | 202 | 산불/호우 | 아니오 | 30 | - |");
    }

    // 틀린 쌍 표에 "종류"(놓침/잘못 묶음) 칸이 있어야 이전 리포트와 비교할 때 종류가 바뀐 쌍을 가릴 수 있고,
    // diff 를 받으면 "이전 대비" 절(요약 줄 + 새로 틀린 쌍 표 + 고쳐진 쌍)을 쓰며 안 받으면 그 절을 아예 쓰지 않는다.
    // Red 단계: 뼈대는 종류 칸도 "## 이전 대비" 절도 쓰지 않아 첫 단언에서 실패한다.
    @Test
    void 종류_칸을_쓰고_diff가_있을_때만_이전_대비_절을_쓴다() {
        // 준비: 이번에 틀린 쌍 하나(301,302 놓침). 이전 리포트에는 없던 쌍이라 "새로 틀림", 101~102 는 이전에만 있어 "고쳐짐"
        Row newWrong = new Row(301, 302, WrongKind.MISSED, new PairFacts("산불", "산불", true, 5, 0.2));
        ReportDiff.Result diff = new ReportDiff.Result(
                List.of(new ReportedPair(101, 102, ReportedPair.Kind.MISSED)),
                List.of(new ReportedPair(301, 302, ReportedPair.Kind.MISSED)),
                List.of());

        // 실행: diff 를 준 경우와 안 준 경우
        String withDiff = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(newWrong), diff);
        String withoutDiff = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(newWrong));

        // 검증: 기존 6칸 뒤에 "종류" 칸이 붙어 표 행이 "... | 0.200 | 놓침 |" 로 끝난다(앞 6칸을 그대로 두어 옛 표 읽기와 호환)
        assertThat(withDiff).contains("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |")
                .contains("| 301 | 302 | 산불 | 예 | 5 | 0.200 | 놓침 |");
        // 검증: 이전 대비 절 — 요약 줄(고쳐짐 1, 새로 틀림 1, 종류 바뀜 0), 새로 틀린 쌍 표(사실은 이번 행에서 가져옴), 고쳐진 쌍
        assertThat(withDiff)
                .contains("## 이전 대비")
                .contains("- 고쳐짐 1쌍, 새로 틀림 1쌍, 종류 바뀜 0쌍")
                .contains("### 새로 틀린 쌍")
                .contains("### 고쳐진 쌍")
                .contains("- 101 ↔ 102");
        // 검증: --eval.baseline 을 안 준 실행은 절 자체가 없다(빈 절이 "퇴행 없음" 으로 읽히지 않게)
        assertThat(withoutDiff).doesNotContain("## 이전 대비");
    }
}
