package com.disaster.alert.alertapi.domain.event.evaluation;

import org.springframework.boot.ApplicationArguments;

/** 평가 실행기 인자({@code --eval.*}). */
// Red 단계 뼈대 — 구현은 Green 에서
public record EvaluationRunOptions(String dataset, String labelsPath, String reportPath) {

    public static EvaluationRunOptions parse(ApplicationArguments args) {
        return new EvaluationRunOptions(null, null, null);
    }
}
