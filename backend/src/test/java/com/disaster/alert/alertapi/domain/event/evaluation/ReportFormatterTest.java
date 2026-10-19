package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult;
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

    // 리포트 요약은 사람이 숫자 두 개만 보고 상태를 파악하게 하는 것이 목적이라 문장형 형식이 고정돼 있다.
    // Red 단계: 뼈대는 빈 문자열을 돌려줘 문장이 하나도 들어 있지 않으므로 실패한다.
    @Test
    void 가이드_예시가_문장형_요약으로_그대로_나온다() {
        // 준비: 모르겠음 3쌍, 미배정 4쌍이 따로 제외된 결과
        PairCountResult result = new PairCountResult(2, 2, 1, 3, 4, OptionalDouble.of(50.0), OptionalDouble.of(100.0 / 3),
                List.of());

        // 실행
        String report = ReportFormatter.format("cluster-eval-2026-10", RUN_AT, result, List.of());

        // 검증: 같은 사건 = 맞게 묶음+놓침 = 4, 묶은 쌍 = 맞게 묶음+잘못 묶음 = 3
        assertThat(report)
                .contains("## 요약")
                .contains("- 같은 사건 4쌍 중 2쌍 묶음, 2쌍 놓침 → 과소병합율 50.0%")
                .contains("- 묶은 3쌍 중 2쌍 맞음, 1쌍 잘못 묶음 → 과병합율 33.3%")
                .contains("- 제외: 모르겠음 3쌍, 이벤트 미배정 4쌍");
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
    // Red 단계: 뼈대는 빈 문자열이라 "과소병합율 -" 가 없어 실패한다.
    @Test
    void 분모가_0이면_비율_자리에_대시() {
        // 준비: 비율이 둘 다 없는(empty) 결과
        PairCountResult result = new PairCountResult(0, 0, 0, 1, 0, OptionalDouble.empty(), OptionalDouble.empty(), List.of());

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, result, List.of());

        // 검증: 퍼센트 기호 없이 "-" 만 적힌다
        assertThat(report).contains("→ 과소병합율 -").contains("→ 과병합율 -");
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
            Row row = new Row(1, 2, new PairFacts("산불", "산불", true, 5, 0.1234));

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
        Row sameType = new Row(101, 102, new PairFacts("산불", "산불", true, 5, 0.2));
        Row diffType = new Row(201, 202, new PairFacts("산불", "호우", false, 30, null));

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(sameType, diffType));

        // 검증: 헤더와 두 행. 같은 유형은 "산불" 하나, 다른 유형은 "산불/호우", 거리 없으면 "-"
        assertThat(report)
                .contains("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |")
                .contains("| 101 | 102 | 산불 | 예 | 5 |")
                .contains("| 201 | 202 | 산불/호우 | 아니오 | 30 | - |");
    }
}
