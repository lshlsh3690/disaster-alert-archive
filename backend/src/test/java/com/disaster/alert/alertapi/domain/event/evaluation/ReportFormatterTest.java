package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.ErrorType;
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
        // 가이드 예시(쌍 6개)의 결과: 맞게 묶음 2, 과소병합 2, 과병합 1, 맞게 나눔 1 → F1 57.1점
        return new PairCountResult(2, 2, 1, 1, 0, 0, OptionalDouble.of(50.0), OptionalDouble.of(100.0 / 3),
                List.of());
    }

    // 대표 지표는 균형 정확도를 먼저 보여 주고, 두 클래스의 근거 개수·보조 F1·판단 없는 기준선을 함께 적는다.
    // Red 단계에서는 기존 요약이 "점수"로 시작하므로 균형 정확도 첫 줄 단언에서 실패한다.
    @Test
    void 요약은_균형_정확도를_먼저_보이고_두_비율과_보조_F1과_기준선을_담는다() {
        // Given: 맞게 묶음 39·과소병합 21·과병합 3·맞게 나눔 27로 같은 사건 60쌍, 다른 사건 30쌍이다.
        // 모르겠음 6쌍은 분모에서 빼며, 기존 세 개수와 F1은 보조 항목에서도 확인한다.
        PairCountResult result = new PairCountResult(39, 21, 3, 27, 6, 0,
                OptionalDouble.of(21 * 100.0 / 60), OptionalDouble.of(3 * 100.0 / 42), List.of());

        // When: 요약과 틀린 쌍 표를 함께 만들되 대표 지표가 요약 첫 줄인지 확인한다.
        String report = ReportFormatter.format("cluster-eval-2026-10", RUN_AT, result, List.of());

        // Then: BA=(65%+90%)/2=77.5%, 기존 F1=78/102=76.5점, 전부 묶음 F1=120/150=80점이다.
        assertThat(report)
                .contains("## 요약\n- 균형 정확도 77.5% — 빠짐없이 묶은 비율과 섞지 않은 비율의 평균")
                .contains("- 빠짐없이 묶은 비율 65.0% — 같은 사건 60쌍 중 39쌍 묶음, 21쌍 과소병합 (과소병합율 35.0%)")
                .contains("- 섞지 않은 비율 90.0% — 다른 사건 30쌍 중 27쌍 따로 둠, 3쌍 과병합")
                .contains("- 보조: 맞게 묶은 비율 92.9% (묶은 42쌍 중 39쌍), F1 76.5점")
                .contains("- 판단 없는 기준선: 전부 하나로 묶음 균형 정확도 50.0% · F1 80.0점 / 전부 따로 둠 50.0% · F1 0.0점")
                .contains("- 제외: 모르겠음 6쌍, 이벤트 미배정 0쌍");
    }

    // 대표 지표가 바뀌어도 기존 F1의 조화평균 정의와 수치는 보조 줄에서 그대로 유지해야 한다.
    // Red 단계의 기존 포매터는 보조 F1 줄이 없어 실패한다.
    @Test
    void 보조_F1은_산술평균이_아니라_조화평균을_유지한다() {
        // Given: 맞게 묶음 10·과소병합 10·과병합 0 → 맞게 묶은 비율 100%, 빠짐없이 묶은 비율 50%다.
        PairCountResult result = new PairCountResult(10, 10, 0, 10, 0, 0,
                OptionalDouble.of(50.0), OptionalDouble.of(0.0), List.of());

        // When: 대표 지표와 별도로 기존 F1이 보조 줄에 표시되는지 본다.
        String report = ReportFormatter.format("ds", RUN_AT, result, List.of());

        // Then: 산술평균 75.0점이 아니라 기존 F1=20/30=66.7점이어야 한다.
        assertThat(report)
                .contains("- 보조: 맞게 묶은 비율 100.0% (묶은 10쌍 중 10쌍), F1 66.7점")
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
    // 새 대표 지표·보조 지표·기준선에도 같은 대시 규칙을 적용한다. 기존 포매터는 새 항목이 없어 실패한다.
    @Test
    void 분모가_0이면_비율_자리에_대시() {
        // Given: 두 클래스의 분모가 모두 없는 결과이며 모르겠음 1쌍만 제외된다.
        PairCountResult result = new PairCountResult(0, 0, 0, 0, 1, 0, OptionalDouble.empty(), OptionalDouble.empty(), List.of());

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, result, List.of());

        // Then: 계산 불가를 0으로 만들거나 단위를 붙이지 않고, 개수와 과소병합율 표시도 유지한다.
        assertThat(report)
                .contains("## 요약\n- 균형 정확도 - — 빠짐없이 묶은 비율과 섞지 않은 비율의 평균")
                .contains("- 빠짐없이 묶은 비율 - — 같은 사건 0쌍 중 0쌍 묶음, 0쌍 과소병합 (과소병합율 -)")
                .contains("- 섞지 않은 비율 - — 다른 사건 0쌍 중 0쌍 따로 둠, 0쌍 과병합")
                .contains("- 보조: 맞게 묶은 비율 - (묶은 0쌍 중 0쌍), F1 -")
                .contains("- 판단 없는 기준선: 전부 하나로 묶음 균형 정확도 - · F1 - / 전부 따로 둠 - · F1 -")
                .doesNotContain("-점", "-%");
    }

    // 실행 PC 의 기본 Locale 이 한국어/유럽형이면 String.format 이 소수점을 콤마로 쓸 수 있다.
    // Locale.ROOT 를 쓰는지 보려고 기본 Locale 을 독일어로 바꿔 확인한다(독일어는 콤마 소수점).
    // 새 요약에서는 보조 비율 66.7%로 검사해, 사라지는 옛 과병합율 문구에 의존하지 않는다.
    @Test
    void 기본_Locale이_콤마_소수점이어도_점으로_쓴다() {
        Locale original = Locale.getDefault();
        Locale.setDefault(Locale.GERMANY);
        try {
            // 준비: 거리 0.1234 가 있는 틀린 쌍 1건
            Row row = new Row(1, 2, ErrorType.MISSED, new PairFacts("산불", "산불", true, 5, 0.1234));

            // 실행
            String report = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(row));

            // 검증: 비율과 거리 모두 점 소수점이고 콤마 소수점은 없다
            assertThat(report).contains("66.7%").contains("0.123").doesNotContain("66,7").doesNotContain("0,123");
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
        Row sameType = new Row(101, 102, ErrorType.MISSED, new PairFacts("산불", "산불", true, 5, 0.2));
        Row diffType = new Row(201, 202, ErrorType.OVER_MERGED, new PairFacts("산불", "호우", false, 30, null));

        // 실행
        String report = ReportFormatter.format("ds", RUN_AT, guideExample(), List.of(sameType, diffType));

        // 검증: 헤더와 두 행. 같은 유형은 "산불" 하나, 다른 유형은 "산불/호우", 거리 없으면 "-"
        // 표 제목과 종류 칸도 과소병합(MISSED)/과병합(OVER_MERGED) 이름을 쓴다
        assertThat(report)
                .contains("## 과소병합 쌍 / 과병합 쌍")
                .contains("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |")
                .contains("| 101 | 102 | 산불 | 예 | 5 |")
                .contains("| 0.200 | 과소병합 |")
                .contains("| 201 | 202 | 산불/호우 | 아니오 | 30 | - | 과병합 |");
    }

}
