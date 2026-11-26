package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.LabelJson.Label;
import com.disaster.alert.alertapi.domain.event.model.EvaluationLabel;
import com.disaster.alert.alertapi.domain.event.repository.ClusteringPairLabelRepository;
import com.disaster.alert.alertapi.domain.event.repository.EvaluationLabelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 판사 일치율 측정(--eval.judge-agreement, 유료)의 옵션 조합 검사(specs/014 계약 3절). 판사를 부르기 전에 막아야 돈이 나가지 않는다.
 * DB·Spring 없이 의존성을 목으로 두고 도구를 직접 만든다.
 */
class EvaluationRunToolJudgeAgreementTest {

    private final EvaluationLabelRepository labelRepository = mock(EvaluationLabelRepository.class);
    private final ClusteringPairLabelRepository pairLabelRepository = mock(ClusteringPairLabelRepository.class);
    private final JudgeAgreementRunner runner = mock(JudgeAgreementRunner.class);
    private final EvaluationRunTool tool = new EvaluationRunTool(mock(AssignmentReader.class), mock(RecheckRunner.class),
            runner, pairLabelRepository, labelRepository, mock(ConfigurableApplicationContext.class));

    // 결정: 리포트 파일은 없앴다(결과는 DB). --eval.report 를 조용히 무시하면 파일이 생긴 줄 알게 되므로 판사를 부르기 전에 멈춘다
    @Test
    void 리포트_경로를_함께_주면_판사를_부르기_전에_멈춘다() {
        // 준비: 셋 라벨 하나
        EvaluationLabel label = mock(EvaluationLabel.class);
        when(label.getAlertAId()).thenReturn(1L);
        when(label.getAlertBId()).thenReturn(2L);
        when(label.getLabel()).thenReturn(Label.SAME);
        when(labelRepository.findByDatasetOrderByIdAsc("set")).thenReturn(List.of(label));

        // 실행·검증
        assertThatThrownBy(() -> tool.run(new DefaultApplicationArguments(
                "--eval.dataset=set", "--eval.judge-agreement", "--eval.report=x.md")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--eval.report");
        verify(runner, never()).run(any(), anyList());
    }

    // 결정: 불일치 쌍 라벨(--eval.pair-labels-as-of)은 판사·시스템이 갈린 어려운 쌍만 모은 것이라 버전 일치율로 쓰지 않는다
    @Test
    void 불일치_쌍_라벨로는_재지_않고_판사를_부르기_전에_멈춘다() {
        // 준비: 기준 시각 전에 저장된 불일치 쌍 라벨 하나(이게 없으면 라벨 읽기 단계에서 다른 이유로 먼저 멈춘다)
        var pairLabel = mock(com.disaster.alert.alertapi.domain.event.model.ClusteringPairLabel.class);
        when(pairLabel.getAlertA()).thenReturn(1L);
        when(pairLabel.getAlertB()).thenReturn(2L);
        when(pairLabel.getLabel()).thenReturn(Label.SAME);
        when(pairLabel.getUpdatedAt()).thenReturn(java.time.LocalDateTime.of(2026, 11, 1, 0, 0));
        when(pairLabelRepository.findAll()).thenReturn(List.of(pairLabel));

        // 실행·검증
        assertThatThrownBy(() -> tool.run(new DefaultApplicationArguments(
                "--eval.pair-labels-as-of=2026-11-25T00:00", "--eval.judge-agreement")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--eval.dataset");
        verify(runner, never()).run(any(), anyList());
    }
}
