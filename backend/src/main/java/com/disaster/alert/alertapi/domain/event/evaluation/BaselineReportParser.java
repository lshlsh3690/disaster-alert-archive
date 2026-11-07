package com.disaster.alert.alertapi.domain.event.evaluation;

import com.disaster.alert.alertapi.domain.event.evaluation.ReportedPair.Kind;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 이전에 만든 평가 리포트(마크다운)에서 틀린 쌍 목록을 다시 읽는다.
 * 6칸(옛 형식)과 7칸("종류" 칸 추가) 표를 모두 읽는다.
 *
 * <p>{@code --eval.baseline} 으로 이전 리포트를 받았을 때 {@link EvaluationRunTool} 이 부른다.
 * 읽을 수 없는 줄은 예외 없이 건너뛴다 — 표 밖의 줄이 섞여 있는 것이 정상이기 때문이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BaselineReportParser {

    // ReportFormatter 가 틀린 쌍 표 위에 쓰는 절 제목. 이 절 안의 표만 읽는다
    private static final String WRONG_PAIRS_HEADING = "## 놓친 쌍 / 잘못 묶은 쌍";
    private static final int OLD_COLUMNS = 6;
    private static final int KIND_COLUMN_INDEX = 6;

    /**
     * @return 표 순서대로의 틀린 쌍. 절이 없거나 표가 비어 있으면 빈 목록
     */
    public static List<ReportedPair> parse(String reportMarkdown) {
        List<ReportedPair> pairs = new ArrayList<>();
        boolean inWrongPairsSection = false;
        for (String line : reportMarkdown.split("\\R")) {
            // 1) 제목 줄이 나오면 절을 바꾼다 — 이전 대비 절의 "새로 틀린 쌍" 표(같은 열 구성)를 이중으로 읽지 않으려고
            //    틀린 쌍 절 안의 표만 읽는다
            if (line.startsWith("#")) {
                inWrongPairsSection = line.trim().equals(WRONG_PAIRS_HEADING);
                continue;
            }
            if (inWrongPairsSection && line.startsWith("|")) {
                parseRow(line).ifPresent(pairs::add);
            }
        }
        return pairs;
    }

    // 2) 표 한 줄을 읽는다. 머리글·구분선(|---|)은 앞 두 칸이 숫자가 아니라 여기서 걸러진다
    private static Optional<ReportedPair> parseRow(String line) {
        // 유형 칸의 슬래시("기타/강풍")나 거리 칸의 "-" 는 구분자와 무관하다. 구분자는 '|' 뿐이다
        String[] cells = line.substring(1).split("\\|", -1);
        // 끝의 '|' 뒤에 남는 빈 조각을 뺀 실제 칸 수
        int columns = cells.length - 1;
        if (columns < OLD_COLUMNS) {
            return Optional.empty();
        }
        try {
            long a = Long.parseLong(cells[0].trim());
            long b = Long.parseLong(cells[1].trim());
            // 3) 7번째 칸이 없으면(옛 리포트) 종류를 추측하지 않고 UNKNOWN 으로 둔다
            Kind kind = columns > OLD_COLUMNS ? parseKind(cells[KIND_COLUMN_INDEX].trim()) : Kind.UNKNOWN;
            return Optional.of(new ReportedPair(a, b, kind));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Kind parseKind(String text) {
        return switch (text) {
            case "놓침" -> Kind.MISSED;
            case "잘못 묶음" -> Kind.OVER_MERGED;
            default -> Kind.UNKNOWN;
        };
    }
}
