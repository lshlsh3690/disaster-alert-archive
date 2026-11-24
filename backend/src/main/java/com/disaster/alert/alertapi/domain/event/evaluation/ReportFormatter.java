package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.AssignmentReader.PairFacts;
import com.disaster.alert.alertapi.domain.event.evaluation.PairCountResult.ErrorType;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * 평가 결과를 마크다운 리포트로 만든다. 요약 형식은 specs/013-balanced-accuracy-metric/contracts/report-and-api.md 1절.
 * 대표 균형 정확도와 보조 F1을 함께 표시하며, 기존 머리글과 틀린 쌍 표 형식은 유지한다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportFormatter {

    /** 틀린 쌍(과소병합·과병합) 한 줄: 알림 id 둘, 종류, 사실. */
    public record Row(long alertA, long alertB, ErrorType kind, PairFacts facts) {
    }

    private static final String WRONG_PAIRS_HEADING = "## 과소병합 쌍 / 과병합 쌍";

    private static final DateTimeFormatter RUN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 같은 평가 집합의 판단 없는 기준선(전부 묶음·전부 따로)을 요약에 함께 표시한 리포트를 반환한다.
     * 기준선은 결과 개수만으로 정해지므로 호출자가 따로 계산해 넘기지 않는다. 계산 불가 비율·점수는 대시로 쓴다.
     */
    public static String format(String datasetName, LocalDateTime runAt,
                                PairCountResult result, List<Row> wrongRows) {
        PairCountResult allMerged = TrivialBaselines.allMerged(result);
        PairCountResult allSeparated = TrivialBaselines.allSeparated(result);
        StringBuilder sb = new StringBuilder();
        // 1) 머리: 데이터셋 이름과 실행 시각만
        sb.append("# 클러스터링 평가 - ").append(datasetName).append('\n');
        sb.append("- 실행 시각: ").append(runAt.format(RUN_AT_FORMAT)).append("\n\n");

        // 2) 요약을 새 계약에 맞추되 머리글과 틀린 쌍 표는 기존 위치·형식을 유지한다.
        appendSummary(sb, result, allMerged, allSeparated);

        // 3) 틀린 쌍 표: 이유는 계산하지 않고 사실만 적는다
        sb.append(WRONG_PAIRS_HEADING).append('\n');
        // "종류" 칸은 맨 끝: 앞 6칸 구성을 그대로 두어 옛 6칸 표와 같은 방식으로 읽히게 한다
        appendTableHeader(sb);
        for (Row r : wrongRows) {
            appendRow(sb, r.alertA(), r.alertB(), kindLabel(r.kind()), r.facts());
        }
        return sb.toString();
    }

    private static void appendSummary(StringBuilder sb, PairCountResult result,
                                      PairCountResult allMerged, PairCountResult allSeparated) {
        // 1) 두 클래스의 정답률과 근거 개수를 먼저 보여 구성비가 다른 평가 결과도 비교하게 한다.
        sb.append("## 요약\n");
        sb.append("- 균형 정확도 ").append(percent(result.balancedAccuracy()))
                .append(" — 빠짐없이 묶은 비율과 섞지 않은 비율의 평균\n");
        sb.append("- 빠짐없이 묶은 비율 ").append(percent(result.completeness())).append(" — 같은 사건 ")
                .append(result.sameEventPairs()).append("쌍 중 ").append(result.correctlyMerged())
                .append("쌍 묶음, ").append(result.missed()).append("쌍 과소병합 (과소병합율 ")
                .append(percent(result.underMergeRate())).append(")\n");
        sb.append("- 섞지 않은 비율 ").append(percent(result.separation())).append(" — 다른 사건 ")
                .append(result.differentEventPairs()).append("쌍 중 ").append(result.correctlySeparated())
                .append("쌍 따로 둠, ").append(result.overMerged()).append("쌍 과병합\n");
        // 2) 기존 F1과 맞게 묶은 비율은 보조로 남겨 지난 리포트의 수치와 이어 읽을 수 있게 한다.
        sb.append("- 보조: 맞게 묶은 비율 ").append(percent(result.purity())).append(" (묶은 ")
                .append(result.mergedPairs()).append("쌍 중 ").append(result.correctlyMerged())
                .append("쌍), F1 ").append(score(result.score())).append('\n');
        sb.append("- 판단 없는 기준선: 전부 하나로 묶음 균형 정확도 ")
                .append(percent(allMerged.balancedAccuracy())).append(" · F1 ").append(score(allMerged.score()))
                .append(" / 전부 따로 둠 ").append(percent(allSeparated.balancedAccuracy()))
                .append(" · F1 ").append(score(allSeparated.score())).append('\n');
        sb.append("- 제외: 모르겠음 ").append(result.excludedUnsure()).append("쌍, 이벤트 미배정 ")
                .append(result.excludedUnassigned()).append("쌍\n");
    }

    private static void appendTableHeader(StringBuilder sb) {
        sb.append("| alertA | alertB | 유형 | 같은 시군구 | 간격(시간) | 본문 거리 | 종류 |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
    }

    private static String kindLabel(ErrorType kind) {
        return kind == ErrorType.MISSED ? "과소병합" : "과병합";
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
