package com.disaster.alert.alertapi.domain.event.tool;

import com.disaster.alert.alertapi.domain.event.evaluation.Confusion;
import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelLoader;
import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationReportFormatter;
import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationStratum;
import com.disaster.alert.alertapi.domain.event.evaluation.EventEvaluationService;
import com.disaster.alert.alertapi.domain.event.evaluation.PairwiseReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EvaluationRunToolTest {

    @TempDir
    Path tmp;

    EvaluationLabelLoader loader;
    EventEvaluationService service;
    EvaluationRunTool tool;
    PairwiseReport report;

    @BeforeEach
    void setUp() {
        loader = mock(EvaluationLabelLoader.class);
        service = mock(EventEvaluationService.class);
        tool = new EvaluationRunTool(loader, service);
        report = new PairwiseReport(new Confusion(69, 0, 42, 36, 1.0, 0.6216, 0.7667),
                new EnumMap<>(EvaluationStratum.class), List.of(), 0);
        when(service.evaluate(anyString())).thenReturn(report);
    }

    @Test
    void labels_옵션이_있으면_파일_내용으로_적재한_뒤_평가한다() throws Exception {
        Path csv = tmp.resolve("labels.csv");
        Files.writeString(csv, "한글,내용\n1,2\n", StandardCharsets.UTF_8);

        tool.run(new DefaultApplicationArguments("--eval.dataset=ds", "--eval.labels=" + csv));

        InOrder order = inOrder(loader, service);
        order.verify(loader).load("ds", "한글,내용\n1,2\n");
        order.verify(service).evaluate("ds");
    }

    @Test
    void labels_옵션이_없으면_적재하지_않고_평가만_한다() {
        tool.run(new DefaultApplicationArguments("--eval.dataset=ds"));

        verify(loader, never()).load(anyString(), anyString());
        verify(service).evaluate("ds");
    }

    @Test
    void report_옵션이_있으면_부모_디렉터리까지_만들어_포맷터_결과를_UTF8로_쓴다() throws Exception {
        Path out = tmp.resolve("sub/dir/out.md");

        tool.run(new DefaultApplicationArguments("--eval.dataset=ds", "--eval.report=" + out));

        assertThat(out).exists();
        String written = Files.readString(out, StandardCharsets.UTF_8);
        assertThat(written).isEqualTo(EvaluationReportFormatter.toMarkdown("ds", report));
        assertThat(written).isNotEmpty();
    }

    @Test
    void report_옵션이_없으면_파일을_만들지_않는다() throws Exception {
        tool.run(new DefaultApplicationArguments("--eval.dataset=ds"));

        verify(service).evaluate("ds");
        try (var files = Files.list(tmp)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void dataset_옵션이_없으면_예외이고_아무것도_호출하지_않는다() {
        assertThatThrownBy(() -> tool.run(new DefaultApplicationArguments("--eval.report=" + tmp.resolve("o.md"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eval.dataset");

        verifyNoInteractions(loader, service);
    }

    @Test
    void labels_파일이_없으면_파일명을_담은_예외이고_적재도_평가도_하지_않는다() {
        Path missing = tmp.resolve("nope.csv");

        assertThatThrownBy(() -> tool.run(new DefaultApplicationArguments(
                "--eval.dataset=ds", "--eval.labels=" + missing)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nope.csv");

        verify(loader, never()).load(anyString(), anyString());
        verify(service, never()).evaluate(anyString());
    }
}
