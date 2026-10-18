package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationReportFormatterTest {

    private static Confusion conf(int tp, int fp, int fn, int tn, double p, double r, double f1) {
        return new Confusion(tp, fp, fn, tn, p, r, f1);
    }

    /** tp69 fn42 tn36 fp0 → precision 1.0, recall 69/111=0.6216. */
    private static PairwiseReport sample(List<ErrorCase> errors, int unsure) {
        Map<EvaluationStratum, Confusion> by = new EnumMap<>(EvaluationStratum.class);
        by.put(EvaluationStratum.TRAP, conf(1, 0, 2, 3, 1.0, 0.3333, 0.5));
        by.put(EvaluationStratum.GENERAL, conf(60, 0, 20, 30, 1.0, 0.75, 0.8571));
        by.put(EvaluationStratum.BOUNDARY, conf(8, 0, 20, 3, 1.0, 0.2857, 0.4444));
        return new PairwiseReport(conf(69, 0, 42, 36, 1.0, 69 / 111.0, 2 * 69 / (2 * 69 + 42.0)),
                by, errors, unsure);
    }

    @Test
    void 제목에_dataset_이름과_전체_지표가_들어간다() {
        String md = EvaluationReportFormatter.toMarkdown("ds-1", sample(List.of(), 0));

        assertThat(md).contains("ds-1");
        assertThat(md).contains("0.6216").contains("1.0000");
        assertThat(md).contains("69").contains("42").contains("36");
    }

    @Test
    void 층별_표는_GENERAL_BOUNDARY_TRAP_순서() {
        String md = EvaluationReportFormatter.toMarkdown("ds", sample(List.of(), 0));

        int g = md.indexOf("GENERAL");
        int b = md.indexOf("BOUNDARY");
        int t = md.indexOf("TRAP");
        assertThat(g).isGreaterThanOrEqualTo(0);
        assertThat(b).isGreaterThan(g);
        assertThat(t).isGreaterThan(b);
    }

    @Test
    void report_에_없는_층은_표에_나오지_않는다() {
        Map<EvaluationStratum, Confusion> by = new EnumMap<>(EvaluationStratum.class);
        by.put(EvaluationStratum.GENERAL, conf(1, 0, 0, 1, 1.0, 1.0, 1.0));
        var report = new PairwiseReport(conf(1, 0, 0, 1, 1.0, 1.0, 1.0), by, List.of(), 0);

        String md = EvaluationReportFormatter.toMarkdown("ds", report);

        assertThat(md).contains("GENERAL").doesNotContain("BOUNDARY").doesNotContain("TRAP");
    }

    @Test
    void UNSURE_제외_건수가_나온다() {
        String md = EvaluationReportFormatter.toMarkdown("ds", sample(List.of(), 7));

        assertThat(md).contains("UNSURE").contains("7");
    }

    @Test
    void 원인별_건수_요약은_0건_원인도_표시한다() {
        var errors = List.of(
                new ErrorCase(1, 2, EvaluationStratum.GENERAL, ErrorCause.RETRIEVAL_MISS),
                new ErrorCase(3, 4, EvaluationStratum.TRAP, ErrorCause.RETRIEVAL_MISS),
                new ErrorCase(5, 6, EvaluationStratum.BOUNDARY, ErrorCause.DECISION_MISS));

        String md = EvaluationReportFormatter.toMarkdown("ds", sample(errors, 0));

        assertThat(md).contains("과병합").contains("검색 실패").contains("판정 실패").contains("알 수 없음");
        assertThat(md).containsPattern("검색 실패\\D*2");
        assertThat(md).containsPattern("판정 실패\\D*1");
        assertThat(md).containsPattern("과병합\\D*0");
    }

    @Test
    void 오류_사례_표에_알림_ID_층_원인이_나온다() {
        var errors = List.of(new ErrorCase(101, 202, EvaluationStratum.BOUNDARY, ErrorCause.OVER_MERGE));

        String md = EvaluationReportFormatter.toMarkdown("ds", sample(errors, 0));

        assertThat(md).contains("101").contains("202").contains("BOUNDARY");
        assertThat(md).doesNotContain("오류 없음");
    }

    @Test
    void 오류가_없으면_표_대신_오류_없음_한_줄() {
        String md = EvaluationReportFormatter.toMarkdown("ds", sample(List.of(), 0));

        assertThat(md).contains("오류 없음");
    }

    @Test
    void 빈_report_도_NaN_없이_0_0000() {
        var empty = new PairwiseReport(conf(0, 0, 0, 0, 0, 0, 0), new EnumMap<>(EvaluationStratum.class), List.of(), 0);

        String md = EvaluationReportFormatter.toMarkdown("ds", empty);

        assertThat(md).contains("0.0000").doesNotContain("NaN");
    }

    @Test
    void dataset_이_null_이면_예외() {
        assertThatThrownBy(() -> EvaluationReportFormatter.toMarkdown(null, sample(List.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
