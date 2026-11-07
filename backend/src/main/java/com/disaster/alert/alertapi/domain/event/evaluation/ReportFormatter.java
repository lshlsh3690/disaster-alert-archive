package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * 평가 결과를 마크다운 리포트로 만든다. 형식은 docs/CLUSTERING_EVAL_GUIDE.md "리포트" 절.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportFormatter {

    /** 틀린 쌍(놓침·잘못 묶음) 한 줄: 알림 id 둘과 사실. */
    public record Row(long alertA, long alertB, PairFacts facts) {
    }

    private static final DateTimeFormatter RUN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows) {
        StringBuilder sb = new StringBuilder();
        // 1) 머리: 데이터셋 이름과 실행 시각만
        sb.append("# 클러스터링 평가 - ").append(datasetName).append('\n');
        sb.append("- 실행 시각: ").append(runAt.format(RUN_AT_FORMAT)).append("\n\n");

        // 2) 요약: 분모는 같은 사건=맞게 묶음+놓침, 묶은 쌍=맞게 묶음+잘못 묶음
        int same = result.correctlyMerged() + result.missed();
        int merged = result.correctlyMerged() + result.overMerged();
        sb.append("## 요약\n");
        sb.append("- 같은 사건 ").append(same).append("쌍 중 ").append(result.correctlyMerged())
                .append("쌍 묶음, ").append(result.missed()).append("쌍 놓침 → 과소병합율 ")
                .append(percent(result.underMergeRate())).append('\n');
        sb.append("- 묶은 ").append(merged).append("쌍 중 ").append(result.correctlyMerged())
                .append("쌍 맞음, ").append(result.overMerged()).append("쌍 잘못 묶음 → 과병합율 ")
                .append(percent(result.overMergeRate())).append('\n');
        sb.append("- 제외: 모르겠음 ").append(result.excludedUnsure()).append("쌍, 이벤트 미배정 ")
                .append(result.excludedUnassigned()).append("쌍\n");

        // 3) 틀린 쌍 표: 이유는 계산하지 않고 사실만 적는다
        sb.append("## 놓친 쌍 / 잘못 묶은 쌍\n");
        sb.append("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Row r : wrongRows) {
            appendRow(sb, r);
        }
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, Row r) {
        PairFacts f = r.facts();
        // 임베딩 없음은 0 이 아니라 "-" — 0 은 "아주 비슷" 으로 오해된다
        String distance = f.embeddingDistance() == null
                ? "-" : String.format(Locale.ROOT, "%.3f", f.embeddingDistance());
        sb.append("| ").append(r.alertA()).append(" | ").append(r.alertB())
                .append(" | ").append(f.type())
                .append(" | ").append(f.sameSigungu() ? "예" : "아니오")
                .append(" | ").append(f.gapHours())
                .append(" | ").append(distance).append(" |\n");
    }

    // Locale.ROOT: 실행 PC 가 콤마 소수점 Locale 이어도 점으로 쓴다. 분모 0 이면 "-"
    private static String percent(OptionalDouble rate) {
        return rate.isPresent() ? String.format(Locale.ROOT, "%.1f%%", rate.getAsDouble()) : "-";
    }
}
