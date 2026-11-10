package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
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

    /** 틀린 쌍(놓침·잘못 묶음) 한 줄: 알림 id 둘, 종류, 사실. */
    public record Row(long alertA, long alertB, WrongKind kind, PairFacts facts) {
    }

    private static final String WRONG_PAIRS_HEADING = "## 놓친 쌍 / 잘못 묶은 쌍";

    private static final DateTimeFormatter RUN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows) {
        StringBuilder sb = new StringBuilder();
        // 1) 머리: 데이터셋 이름과 실행 시각만
        sb.append("# 클러스터링 평가 - ").append(datasetName).append('\n');
        sb.append("- 실행 시각: ").append(runAt.format(RUN_AT_FORMAT)).append("\n\n");

        // 2) 요약: 점수 한 줄로 전체 품질을 먼저 보이고, 그 재료인 맞게 묶은 비율·빠짐없이 묶은 비율을 근거 개수와 함께 적는다
        //    분모는 묶은 쌍 = 맞게 묶음+잘못 묶음, 같은 사건 쌍 = 맞게 묶음+놓침. 옛 과병합율·과소병합율은
        //    괄호에 남겨 이전 리포트와 이어 읽게 한다(PairCounter 가 계산해 넘긴 값을 그대로 쓴다)
        int same = result.correctlyMerged() + result.missed();
        int merged = result.correctlyMerged() + result.overMerged();
        sb.append("## 요약\n");
        sb.append("- 점수 ").append(score(result.score())).append(" — 맞게 묶은 비율과 빠짐없이 묶은 비율의 조화평균\n");
        sb.append("- 맞게 묶은 비율 ").append(percent(result.purity())).append(" — 묶은 ").append(merged).append("쌍 중 ")
                .append(result.correctlyMerged()).append("쌍 맞음, ").append(result.overMerged())
                .append("쌍 잘못 묶음 (과병합율 ").append(percent(result.overMergeRate())).append(")\n");
        sb.append("- 빠짐없이 묶은 비율 ").append(percent(result.completeness())).append(" — 같은 사건 ").append(same)
                .append("쌍 중 ").append(result.correctlyMerged()).append("쌍 묶음, ").append(result.missed())
                .append("쌍 놓침 (과소병합율 ").append(percent(result.underMergeRate())).append(")\n");
        sb.append("- 제외: 모르겠음 ").append(result.excludedUnsure()).append("쌍, 이벤트 미배정 ")
                .append(result.excludedUnassigned()).append("쌍\n");

        // 3) 틀린 쌍 표: 이유는 계산하지 않고 사실만 적는다
        sb.append(WRONG_PAIRS_HEADING).append('\n');
        // "종류" 칸은 맨 끝: 앞 6칸 구성을 그대로 두어 옛 6칸 표와 같은 방식으로 읽히게 한다
        appendTableHeader(sb);
        for (Row r : wrongRows) {
            appendRow(sb, r.alertA(), r.alertB(), kindLabel(r.kind()), r.facts());
        }
        return sb.toString();
    }

    private static void appendTableHeader(StringBuilder sb) {
        sb.append("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
    }

    private static String kindLabel(WrongKind kind) {
        return kind == WrongKind.MISSED ? "놓침" : "잘못 묶음";
    }

    private static void appendRow(StringBuilder sb, long alertA, long alertB, String kind, PairFacts f) {
        // 임베딩 없음은 0 이 아니라 "-" — 0 은 "아주 비슷" 으로 오해된다
        String distance = f.embeddingDistance() == null
                ? "-" : String.format(Locale.ROOT, "%.3f", f.embeddingDistance());
        sb.append("| ").append(alertA).append(" | ").append(alertB)
                .append(" | ").append(f.type())
                .append(" | ").append(f.sameSigungu() ? "예" : "아니오")
                .append(" | ").append(f.gapHours())
                .append(" | ").append(distance)
                .append(" | ").append(kind).append(" |\n");
    }

    // 점수는 퍼센트 기호 대신 "점" 을 붙인다. 분모 0 이면 "-" 라 "-점" 이 되지 않게 단위는 값이 있을 때만 붙인다
    private static String score(OptionalDouble score) {
        return score.isPresent() ? String.format(Locale.ROOT, "%.1f점", score.getAsDouble()) : "-";
    }

    // Locale.ROOT: 실행 PC 가 콤마 소수점 Locale 이어도 점으로 쓴다. 분모 0 이면 "-"
    private static String percent(OptionalDouble rate) {
        return rate.isPresent() ? String.format(Locale.ROOT, "%.1f%%", rate.getAsDouble()) : "-";
    }
}
