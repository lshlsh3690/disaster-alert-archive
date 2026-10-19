package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 평가 결과를 마크다운 리포트로 만든다. 형식은 docs/CLUSTERING_EVAL_GUIDE.md "리포트" 절.
 */
// Red 단계 뼈대 — 구현은 Green 에서
public final class ReportFormatter {

    /** 틀린 쌍(놓침·잘못 묶음) 한 줄: 알림 id 둘과 사실. */
    public record Row(long alertA, long alertB, PairFacts facts) {
    }

    private ReportFormatter() {
    }

    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows) {
        return "";
    }
}
