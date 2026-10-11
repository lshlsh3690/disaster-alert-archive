package com.disaster.alert.alertapi.domain.event.tool;

import com.disaster.alert.alertapi.domain.event.evaluation.EvaluationLabelLoader;
import com.disaster.alert.alertapi.domain.event.evaluation.EventEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 클러스터링 평가 실행기 ({@code --eval.dataset=..., --eval.labels=..., --eval.report=...}). */
// Red 단계 뼈대 — 구현은 Green 에서
@Component
@Profile("eval")
@RequiredArgsConstructor
@Slf4j
public class EvaluationRunTool implements ApplicationRunner {

    private final EvaluationLabelLoader labelLoader;
    private final EventEvaluationService evaluationService;

    @Override
    public void run(ApplicationArguments args) {
        // 아무것도 하지 않는다
    }
}
