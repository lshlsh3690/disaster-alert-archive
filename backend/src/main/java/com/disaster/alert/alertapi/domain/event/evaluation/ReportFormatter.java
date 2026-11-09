package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.WrongKind;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * 평가 결과를 마크다운 리포트로 만든다. 형식은 docs/CLUSTERING_EVAL_GUIDE.md "리포트" 절.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportFormatter {

    /** 틀린 쌍(놓침·잘못 묶음) 한 줄: 알림 id 둘, 종류, 사실. */
    public record Row(long alertA, long alertB, WrongKind kind, PairFacts facts) {
    }

    /**
     * 틀린 쌍 표 위에 쓰는 절 제목. {@link BaselineReportParser} 가 이 제목으로 표를 찾으므로 같은 상수를 공유한다.
     * 쓰는 쪽(여기)이 원본이라 읽는 쪽이 참조한다 — 문자열이 두 곳에 따로 있으면 한쪽만 고쳐졌을 때
     * 파서가 아무것도 못 읽는 것이 조용히 "틀린 쌍 0개" 로 보이기 때문이다.
     */
    static final String WRONG_PAIRS_HEADING = "## 놓친 쌍 / 잘못 묶은 쌍";

    private static final DateTimeFormatter RUN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows) {
        return format(datasetName, runAt, result, wrongRows, null);
    }

    /**
     * @param diff 이전 리포트와의 비교 결과. null 이면 "이전 대비" 절을 쓰지 않는다.
     */
    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows, ReportDiff.Result diff) {
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

        // 4) 이전 리포트를 받았을 때만 비교 절을 쓴다 — 안 받았는데 빈 절을 쓰면 "퇴행 없음" 으로 읽힌다
        if (diff != null) {
            appendDiff(sb, diff, wrongRows);
        }
        return sb.toString();
    }

    private static void appendTableHeader(StringBuilder sb) {
        sb.append("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
    }

    // 요약 줄 → 새로 틀린 쌍(가장 중요: 튜닝이 망가뜨린 것) → 고쳐진 쌍 → 종류 바뀐 쌍
    private static void appendDiff(StringBuilder sb, ReportDiff.Result diff, List<Row> wrongRows) {
        sb.append("\n## 이전 대비\n");
        sb.append("- 고쳐짐 ").append(diff.fixed().size()).append("쌍, 새로 틀림 ").append(diff.newlyWrong().size())
                .append("쌍, 종류 바뀜 ").append(diff.kindChanged().size()).append("쌍\n");
        // "고쳐짐" 은 "이전에 틀렸고 이번 목록에 없음" 으로만 센다. 라벨 변경·라벨 삭제·알림 부재로 평가에서 빠진 쌍도
        // 섞여 개선 건수가 부풀 수 있다. "새로 틀림" 은 영향이 없어 퇴행 확인 기준으로 안내한다
        sb.append("- 주의: \"고쳐짐\" 에는 라벨이 바뀌었거나 평가에서 빠진 쌍도 섞인다. 퇴행 확인은 \"새로 틀림\" 을 본다.\n");

        // 사실(유형·간격·거리)은 diff 가 아니라 이번 실행의 행에서 가져온다. (a,b) 순서가 다를 수 있어 쌍 키로 찾는다
        Map<ReportDiff.PairKey, Row> rowsByPair = new HashMap<>();
        for (Row r : wrongRows) {
            rowsByPair.put(ReportDiff.PairKey.of(r.alertA(), r.alertB()), r);
        }
        sb.append("\n### 새로 틀린 쌍\n");
        appendTableHeader(sb);
        for (ReportedPair p : diff.newlyWrong()) {
            Row row = rowsByPair.get(ReportDiff.PairKey.of(p.alertA(), p.alertB()));
            // newlyWrong 은 이번 틀린 쌍에서 나오므로 행이 항상 있다. 없으면 사실 칸만 비워 두고 id 와 종류는 남긴다
            if (row == null) {
                sb.append("| ").append(p.alertA()).append(" | ").append(p.alertB())
                        .append(" | - | - | - | - | ").append(kindLabel(p.kind())).append(" |\n");
            } else {
                appendRow(sb, p.alertA(), p.alertB(), kindLabel(p.kind()), row.facts());
            }
        }
        sb.append("\n### 고쳐진 쌍\n");
        for (ReportedPair p : diff.fixed()) {
            sb.append("- ").append(p.alertA()).append(" ↔ ").append(p.alertB()).append('\n');
        }
        if (!diff.kindChanged().isEmpty()) {
            sb.append("\n### 종류 바뀐 쌍\n");
            for (ReportDiff.KindChange c : diff.kindChanged()) {
                sb.append("- ").append(c.alertA()).append(" ↔ ").append(c.alertB()).append(": ")
                        .append(kindLabel(c.before())).append(" → ").append(kindLabel(c.after())).append('\n');
            }
        }
    }

    private static String kindLabel(WrongKind kind) {
        return kind == WrongKind.MISSED ? "놓침" : "잘못 묶음";
    }

    // UNKNOWN(옛 6칸 리포트에서만 나옴)은 "-" 로 쓴다. 놓침/잘못 묶음으로 추측해 쓰지 않는다
    private static String kindLabel(ReportedPair.Kind kind) {
        return switch (kind) {
            case MISSED -> "놓침";
            case OVER_MERGED -> "잘못 묶음";
            case UNKNOWN -> "-";
        };
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
