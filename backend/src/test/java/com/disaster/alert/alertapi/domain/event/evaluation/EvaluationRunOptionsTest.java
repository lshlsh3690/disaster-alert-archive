package com.disaster.alert.alertapi.domain.event.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRunOptionsTest {

    @Test
    void 세_옵션을_모두_읽는다() {
        var o = EvaluationRunOptions.parse(new DefaultApplicationArguments(
                "--eval.dataset=x", "--eval.labels=a.csv", "--eval.report=out.md"));

        assertThat(o.dataset()).isEqualTo("x");
        assertThat(o.labelsPath()).isEqualTo("a.csv");
        assertThat(o.reportPath()).isEqualTo("out.md");
    }

    @Test
    void labels_report_는_선택이라_없으면_null() {
        var o = EvaluationRunOptions.parse(new DefaultApplicationArguments("--eval.dataset=x"));

        assertThat(o.dataset()).isEqualTo("x");
        assertThat(o.labelsPath()).isNull();
        assertThat(o.reportPath()).isNull();
    }

    @Test
    void dataset_이_없으면_예외() {
        assertThatThrownBy(() -> EvaluationRunOptions.parse(new DefaultApplicationArguments("--eval.labels=a.csv")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eval.dataset");
    }

    @Test
    void dataset_이_blank_면_예외() {
        assertThatThrownBy(() -> EvaluationRunOptions.parse(new DefaultApplicationArguments("--eval.dataset=   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eval.dataset");
    }

    @Test
    void 값_앞뒤_공백은_trim() {
        var o = EvaluationRunOptions.parse(new DefaultApplicationArguments(
                "--eval.dataset= x ", "--eval.labels= a.csv ", "--eval.report= out.md "));

        assertThat(o.dataset()).isEqualTo("x");
        assertThat(o.labelsPath()).isEqualTo("a.csv");
        assertThat(o.reportPath()).isEqualTo("out.md");
    }

    @Test
    void 같은_옵션이_여러_번이면_첫_값() {
        var o = EvaluationRunOptions.parse(new DefaultApplicationArguments(
                "--eval.dataset=first", "--eval.dataset=second"));

        assertThat(o.dataset()).isEqualTo("first");
    }
}
