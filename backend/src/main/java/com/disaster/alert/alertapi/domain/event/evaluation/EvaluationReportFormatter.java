package com.disaster.alert.alertapi.domain.event.evaluation;

/**
 * 평가 리포트를 마크다운 한 장으로 만든다. 전/후 비교를 위해 파일로 남기는 용도라 형식을 고정한다.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class EvaluationReportFormatter {

    private EvaluationReportFormatter() {
    }

    public static String toMarkdown(String dataset, PairwiseReport report) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset 이 null 입니다");
        }
        return "# 클러스터링 평가 리포트: " + dataset + "\n";
    }
}
