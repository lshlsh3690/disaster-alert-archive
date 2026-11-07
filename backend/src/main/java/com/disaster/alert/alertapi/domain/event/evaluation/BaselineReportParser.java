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

    private static final int OLD_COLUMNS = 6;
    private static final int KIND_COLUMN_INDEX = 6;

    /**
     * @return 표 순서대로의 틀린 쌍. 틀린 쌍이 0개인 정상 기준선이면 빈 목록
     * @throws IllegalArgumentException 틀린 쌍 절 제목이 아예 없을 때(평가 리포트가 아니거나 제목이 바뀐 경우)
     */
    public static List<ReportedPair> parse(String reportMarkdown) {
        List<ReportedPair> pairs = new ArrayList<>();
        boolean inWrongPairsSection = false;
        boolean headingFound = false;
        for (String line : reportMarkdown.split("\\R")) {
            // 1) 제목 줄이 나오면 절을 바꾼다 — 이전 대비 절의 "새로 틀린 쌍" 표(같은 열 구성)를 이중으로 읽지 않으려고
            //    틀린 쌍 절 안의 표만 읽는다
            if (line.startsWith("#")) {
                // ReportFormatter 가 쓰는 제목과 같은 상수로 비교한다(쓰는 쪽이 원본)
                inWrongPairsSection = line.trim().equals(ReportFormatter.WRONG_PAIRS_HEADING);
                headingFound |= inWrongPairsSection;
                continue;
            }
            if (inWrongPairsSection && line.startsWith("|")) {
                parseRow(line).ifPresent(pairs::add);
            }
        }
        // 2) 제목 자체가 없으면 실패시킨다 — 빈 목록으로 돌려주면 "이전에 틀린 쌍 0개" 로 읽혀
        //    엉뚱한 파일을 넘겼거나 제목이 바뀐 것을 모른 채 모든 틀린 쌍이 "새로 틀림" 으로 나온다.
        //    판별은 제목 유무다: 제목은 있고 표만 빈 정상 기준선(틀린 쌍 0개)은 통과해야 한다
        if (!headingFound) {
            throw new IllegalArgumentException(
                    "기준선 리포트에서 '" + ReportFormatter.WRONG_PAIRS_HEADING
                            + "' 절을 찾지 못했다. 평가 리포트 마크다운이 맞는지, 제목이 바뀌지 않았는지 확인할 것");
        }
        return pairs;
    }

    // 3) 표 한 줄을 읽는다. 머리글·구분선(|---|)은 앞 두 칸이 숫자가 아니라 여기서 걸러진다
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
            // 4) 7번째 칸이 없으면(옛 리포트) 종류를 추측하지 않고 UNKNOWN 으로 둔다
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
