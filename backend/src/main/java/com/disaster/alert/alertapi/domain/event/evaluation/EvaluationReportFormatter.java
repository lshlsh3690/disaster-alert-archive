package com.disaster.alert.alertapi.domain.event.evaluation;

import java.util.List;
import java.util.Locale;

/**
 * 평가 리포트를 마크다운으로 만든다. 전/후 비교를 위해 파일로 남기는 용도라 형식을 고정한다.
 * 소수 포맷에 Locale.ROOT 를 쓰는 이유: 기본 로케일에 따라 소수점이 쉼표로 찍히면 리포트끼리 diff 가 깨진다.
 */
public final class EvaluationReportFormatter {

    private static final EvaluationStratum[] STRATUM_ORDER = {
            EvaluationStratum.GENERAL, EvaluationStratum.BOUNDARY, EvaluationStratum.TRAP};

    private EvaluationReportFormatter() {
    }

    public static String toMarkdown(String dataset, PairwiseReport report) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset 이 null 입니다");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# 클러스터링 평가 리포트: ").append(dataset).append("\n\n");

        sb.append("## 전체 지표\n\n");
        appendMetricHeader(sb, "구분");
        appendMetricRow(sb, "전체", report.overall());

        sb.append("\n## 층별 지표\n\n");
        appendMetricHeader(sb, "층");
        for (EvaluationStratum s : STRATUM_ORDER) {
            Confusion c = report.byStratum().get(s);
            if (c != null) {
                appendMetricRow(sb, s.name(), c);
            }
        }

        sb.append("\n- UNSURE 제외 건수: ").append(report.excludedUnsure()).append("\n");

        appendCauseSummary(sb, report.errors());
        appendErrorCases(sb, report.errors());
        return sb.toString();
    }

    private static void appendMetricHeader(StringBuilder sb, String first) {
        sb.append("| ").append(first).append(" | tp | fp | fn | tn | precision | recall | F1 |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
    }

    private static void appendMetricRow(StringBuilder sb, String label, Confusion c) {
        sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %.4f | %.4f | %.4f |\n",
                label, c.tp(), c.fp(), c.fn(), c.tn(), c.precision(), c.recall(), c.f1()));
    }

    /** 0건인 원인도 보여준다. 줄어든 게 아니라 원래 없었다는 사실이 전/후 비교에 필요하다. */
    private static void appendCauseSummary(StringBuilder sb, List<ErrorCase> errors) {
        sb.append("\n## 오류 원인별 건수\n\n");
        for (ErrorCause cause : ErrorCause.values()) {
            long n = errors.stream().filter(e -> e.cause() == cause).count();
            sb.append("- ").append(causeName(cause)).append(": ").append(n).append("건\n");
        }
    }

    private static void appendErrorCases(StringBuilder sb, List<ErrorCase> errors) {
        sb.append("\n## 오류 사례\n\n");
        if (errors.isEmpty()) {
            sb.append("오류 없음\n");
            return;
        }
        sb.append("| alertA | alertB | 층 | 원인 |\n|---|---|---|---|\n");
        for (ErrorCase e : errors) {
            sb.append("| ").append(e.alertAId()).append(" | ").append(e.alertBId())
                    .append(" | ").append(e.stratum().name())
                    .append(" | ").append(causeName(e.cause())).append(" |\n");
        }
    }

    private static String causeName(ErrorCause cause) {
        return switch (cause) {
            case OVER_MERGE -> "과병합";
            case RETRIEVAL_MISS -> "검색 실패";
            case DECISION_MISS -> "판정 실패";
            case UNKNOWN_MISS -> "알 수 없음";
        };
    }
}
