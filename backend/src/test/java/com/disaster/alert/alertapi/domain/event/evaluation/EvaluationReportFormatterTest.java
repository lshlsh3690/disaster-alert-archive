package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReportFormatterTest {

    /**
     * 리포트는 요약 3줄로 시작한다: 과소병합율·과병합율은 "N쌍 중 M쌍" 과 퍼센트(소수 첫째 자리)를 같이 보여주고,
     * 제외 줄에는 모르겠음·이벤트 미배정 건수가 나온다.
     * Red: 뼈대 포매터는 제목 줄만 출력해 아래 요약 줄 단언들이 실패한다.
     */
    @Test
    void 요약_세_줄에_과소병합율_과병합율_제외_건수가_나온다() {
        // 준비: 묶음 6, 놓침 2 -> 같은 사건 8쌍, 과소병합율 2/8 = 25.0%
        //       묶음 6 + 잘못 묶음 3 = 묶은 9쌍, 과병합율 3/9 = 33.3%
        var report = new PairwiseReport(6, 2, 3, 4, 5, List.of(), List.of());

        String md = EvaluationReportFormatter.toMarkdown("ds-1", report);

        assertThat(md).contains("# 클러스터링 평가 리포트: ds-1");
        assertThat(md).contains("- 같은 사건 8쌍 중 6쌍 묶음, 2쌍 놓침 → 과소병합율 25.0%");
        assertThat(md).contains("- 묶은 9쌍 중 6쌍 맞음, 3쌍 잘못 묶음 → 과병합율 33.3%");
        assertThat(md).contains("- 제외: 모르겠음 4쌍, 이벤트 미배정 5쌍");
    }

    /**
     * 놓친 쌍 표는 열(유형·같은 시군구·간격·본문 거리)과 한 행의 값을 보여준다. 임베딩이 없으면 거리는 "-",
     * 목록이 비면 표 대신 "없음", 분모가 0 이면 비율은 0.0% 가 아니라 "해당 없음"이다.
     * Red: 뼈대 포매터는 표·"없음"·"해당 없음"을 모두 출력하지 않아 단언이 실패한다.
     */
    @Test
    void 놓친_쌍_표_행과_없음_그리고_분모_0이면_해당_없음() {
        var missed = List.of(new ErrorPair(101, 202, new PairFacts("호우/산불", true, 3, null)));
        var withRow = new PairwiseReport(1, 1, 0, 0, 0, missed, List.of());

        String md = EvaluationReportFormatter.toMarkdown("ds", withRow);

        assertThat(md).contains("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |");
        // 거리 null -> "-", 시군구 공유 -> "예", 간격 3 시간
        assertThat(md).contains("| 101 | 202 | 호우/산불 | 예 | 3 | - |");
        // 잘못 묶은 쌍은 없으니 표 대신 "없음"
        assertThat(md).contains("## 잘못 묶은 쌍").contains("없음");

        // 묶음·놓침·잘못 묶음이 모두 0 이면 두 분모가 0 이다
        String empty = EvaluationReportFormatter.toMarkdown("ds",
                new PairwiseReport(0, 0, 0, 0, 0, List.of(), List.of()));
        assertThat(empty).contains("해당 없음").doesNotContain("0.0%").doesNotContain("NaN");
    }
}
